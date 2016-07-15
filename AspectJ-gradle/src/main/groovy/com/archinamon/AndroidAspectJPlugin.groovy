package com.archinamon

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.sun.org.apache.xalan.internal.xsltc.compiler.CompilerException
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile

import static com.archinamon.FilesUtils.*
import static com.archinamon.VariantUtils.*

class AndroidAspectJPlugin implements Plugin<Project> {

    def public static final TAG = "AJC:";
    def private static isLibraryPlugin = false;

    private Project rootProject
    def private plugin;
    private TestedExtension extAndroid
    private boolean hasRetrolambda

    def private buildDirPostfix
    def private buildSideDir

    @Override
    void apply(Project project) {
        rootProject = project;

        if (rootProject.plugins.hasPlugin(AppPlugin)) {
            extAndroid = rootProject.extensions.getByType(AppExtension);
            plugin = rootProject.plugins.getPlugin(AppPlugin);
        } else if (rootProject.plugins.hasPlugin(LibraryPlugin)) {
            extAndroid = rootProject.extensions.getByType(LibraryExtension);
            plugin = rootProject.plugins.getPlugin(LibraryPlugin);
            isLibraryPlugin = true;
        } else {
            throw new GradleException("$TAG You must apply the Android plugin or the Android library plugin");
        }

        def settings = rootProject.extensions.create('aspectj', AspectJExtension);

        androidVariants(isLibraryPlugin, extAndroid).all {
            setupVariant(it);
        }

        testVariants(extAndroid).all {
            setupVariant(it);
        }

        unitTestVariants(extAndroid).all {
            setupVariant(it);
        }

        extAndroid.sourceSets {
            main.java.srcDir('src/main/aspectj');
            androidTest.java.srcDir('src/androidTest/aspectj');
            test.java.srcDir('src/test/aspectj');
        }

        rootProject.repositories { rootProject.repositories.mavenCentral() }
        rootProject.dependencies { compile "org.aspectj:aspectjrt:$settings.ajc" }
        rootProject.afterEvaluate {
            hasRetrolambda = rootProject.plugins.hasPlugin('me.tatarka.retrolambda') as boolean;
            buildDirPostfix = getBuildDirPostfix(hasRetrolambda);

            androidVariants(isLibraryPlugin, extAndroid).all {
                configureAspectJTask(it);
            }

            testVariants(extAndroid).all {
                configureAspectJTask(it, (it as TestVariant).name);
            }

            unitTestVariants(extAndroid).all {
                configureAspectJTask(it, (it as UnitTestVariant).name);
            }
        }
    }

    def private void setupVariant(BaseVariant variant) {
        final def sets = extAndroid.sourceSets;
        final def Closure applier = { String name ->
            applyVariantPreserver(sets, name);
        }

        variant.productFlavors*.name.each(applier);
        variant.buildType*.name.each(applier);
    }

    def private void configureAspectJTask(BaseVariant variant, String testTask = null) {
        final VariantManager manager = getVariantManager(plugin as BasePlugin);
        final AspectJExtension ajParams = rootProject.extensions.findByType(AspectJExtension);

        BaseVariantData<? extends BaseVariantOutputData> data = manager.variantDataList.find { findVarData(it, variant); }

        AbstractCompile javaCompiler = variant.javaCompiler
        if (!javaCompiler instanceof JavaCompile)
            throw new CompilerException("AspectJ plugin doesn't support other java-compilers, only javac");

        final def JavaCompile javaCompile = (JavaCompile) javaCompiler;
        final def pureJavaDestPath = javaCompile.destinationDir;
        def bootClasspath = plugin.properties['runtimeJarList'] ?: extAndroid.bootClasspath;

        def isTestFlav = testTask != null;
        def variantName = variant.name.capitalize();
        def newTaskName = "compile${variantName}XAspectJWithAjc";
        def flavors = variant.productFlavors*.name;

        def srcSet = [variant.buildType.name, *flavors];
        if (isTestFlav) {
            srcSet << 'main';
        }

        buildSideDir = javaCompile.destinationDir;

        rootProject.logger.warn "$TAG Capturing sourceSets: ${srcSet.toListString()}";

        def final String[] srcDirs = srcSet.collect {"src/$it/aspectj"};
        def final FileCollection aspects = new SimpleFileCollection(srcDirs.collect { rootProject.file(it) });
        def final FileCollection aptBuildFiles = getAptBuildFilesRoot(rootProject as Project, variant);

        def aspectjCompile = rootProject.task(newTaskName,
                overwrite: true,
                group: 'build',
                description: 'Compiles AspectJ source code and makes injects into java/kotlin/groovy',
                dependsOn: [javaCompile],
                type: AspectjCompileTask) as AspectjCompileTask;

        aspectjCompile.configure {
            def self = aspectjCompile;

            self.sourceCompatibility = javaCompile.sourceCompatibility;
            self.targetCompatibility = javaCompile.targetCompatibility;
            self.encoding = javaCompile.options.encoding;

            self.destinationDir = rootProject.file(buildSideDir);
            self.aspectPath = setupAspectPath(javaCompile.classpath, aspects, isTestFlav);
            self.classpath = javaCompile.classpath;
            self.bootClasspath = (bootClasspath as List).join(File.pathSeparator);
            self.source = javaCompile.source + aptBuildFiles + aspects;

            //extension params
            self.binaryWeave = ajParams.binaryWeave;
            self.weaveTests = ajParams.weaveTests;
            self.binaryExclude = ajParams.exclude;
            self.logFile = ajParams.logFileName;
            self.weaveInfo = ajParams.weaveInfo;
            self.ignoreErrors = ajParams.ignoreErrors;
            self.addSerialVUID = ajParams.addSerialVersionUID;
            self.interruptOnWarnings = ajParams.interruptOnWarnings;
            self.interruptOnErrors = ajParams.interruptOnErrors;
            self.interruptOnFails = ajParams.interruptOnFails;
        }

        aspectjCompile.doFirst {
            def final buildPath = data.scope.javaOutputDir.absolutePath;

            if (binaryWeave) {
                //experimental: enable binary processing in test-flavours
                if (testTask == null || weaveTests) {
                    configureBinaryWeaving(aspectjCompile, variant, pureJavaDestPath);
                }
            }

            cleanBuildDir(buildPath);
        }

        aspectjCompile.doLast {
            if (isTestFlav) {
                clearExludedBuildSubDirs(aspectjCompile, pureJavaDestPath.absolutePath);
            }
        }

        configureTaskExecutionOrder(javaCompile, aspectjCompile, newTaskName);
    }

    def private configureBinaryWeaving(AspectjCompileTask self, BaseVariant variant, def pureJavaDestPath) {
        def oldDestDir = (pureJavaDestPath as File).absolutePath;

        rootProject.logger.warn "$TAG set path to inpath weaver for $variant.name with $oldDestDir";
        self.addBinaryWeavePath(oldDestDir);

        clearExludedBuildSubDirs(self, oldDestDir);
    }

    def private configureTaskExecutionOrder(JavaCompile javaCompile, AspectjCompileTask aspectjCompile, String taskName) {
        def compileAspectTask = rootProject.tasks.getByName(taskName) as Task;
        javaCompile.finalizedBy compileAspectTask;

        // uPhyca's fix
        // javaCompile.classpath does not contain exploded-aar/**/jars/*.jars till first run
        javaCompile.doLast {
            aspectjCompile.classpath = javaCompile.classpath;
        }
    }

    def private clearExludedBuildSubDirs(AspectjCompileTask self, String oldDestDir) {
        if (!self.binaryExclude.empty) {
            self.binaryExclude.split(",").each {
                new File(concat(oldDestDir, it as String)).deleteDir();
                new File(concat(buildSideDir as String, it as String)).deleteDir();
            }
        }
    }

    @CompileStatic
    def private static cleanBuildDir(def path) {
        def buildDir = new File(path as String);
        if (buildDir.exists()) {
            buildDir.delete();
        }

        buildDir.mkdirs();
    }

    @CompileStatic
    def private static getBuildDirPostfix(def hasRetrolambda) {
        hasRetrolambda ? "retrolambda" : "intermediates/classes";
    }

    @Deprecated
    @CompileStatic
    def private static getNewBuildDir(String rootBuildDir, String postfix, String variantName) {
        "$rootBuildDir/$postfix/$variantName";
    }

    // fix to support Android Pre-processing Tools plugin
    def private static getAptBuildFilesRoot(Project project, BaseVariant variant) {
        def final variantName = variant.name as String;
        def final aptPathShift = "/generated/source/apt/${getSourcePath(variantName)}/";

        return project.files(project.buildDir.path + aptPathShift) as FileCollection;
    }
}
