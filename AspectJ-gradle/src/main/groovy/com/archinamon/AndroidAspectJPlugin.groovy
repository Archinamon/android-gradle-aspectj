package com.archinamon

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.sun.org.apache.xalan.internal.xsltc.compiler.CompilerException
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile

import static com.archinamon.FilesUtils.*
import static com.archinamon.VariantUtils.*

class AndroidAspectJPlugin implements Plugin<Project> {

    def private static isLibraryPlugin = false;

    @Override
    void apply(Project project) {
        final def plugin;
        final AspectJExtension ajParams;

        if (project.plugins.hasPlugin(AppPlugin)) {
            plugin = project.plugins.getPlugin(AppPlugin);
        } else if (project.plugins.hasPlugin(LibraryPlugin)) {
            plugin = project.plugins.getPlugin(LibraryPlugin);
            isLibraryPlugin = true;
        } else {
            throw new GradleException('You must apply the Android plugin or the Android library plugin')
        }

        def android = isLibraryPlugin ? (LibraryExtension) project.android : (AppExtension) project.android;
        DefaultDomainObjectSet<? extends BaseVariant> variants = getVariants(isLibraryPlugin, android);
        ajParams = project.extensions.create('aspectj', AspectJExtension);

        variants.all { BaseVariant variant ->
            final def sets = android.sourceSets;
            final def Closure applier = { String name ->
                applyVariantPreserver(sets, name);
            }

            variant.productFlavors*.name.each(applier);
            variant.buildType*.name.each(applier);
        }

        android.sourceSets {
            main.java.srcDir('src/main/aspectj');
            androidTest.java.srcDir('src/androidTest/aspectj');
            test.java.srcDir('src/test/aspectj');
        }

        project.repositories { project.repositories.mavenCentral() }
        project.dependencies { compile "org.aspectj:aspectjrt:1.8.9" }
        project.afterEvaluate {
            final def hasRetrolambda = project.plugins.hasPlugin('me.tatarka.retrolambda') as boolean;
            final VariantManager manager = getVariantManager(plugin as BasePlugin);

            variants.all { BaseVariant variant -> project.logger.warn "Configuring $variant.name"
                BaseVariantData<? extends BaseVariantOutputData> data = manager.variantDataList.find { findVarData(it, variant); }

                AbstractCompile javaCompiler = variant.javaCompiler
                if (!javaCompiler instanceof JavaCompile)
                    throw new CompilerException("AspectJ plugin doesn't support other java-compilers, only javac");

                final def JavaCompile javaCompile = (JavaCompile) javaCompiler;

                def bootClasspath
                if (plugin.properties['runtimeJarList']) {
                    bootClasspath = plugin.runtimeJarList
                } else {
                    bootClasspath = android.bootClasspath
                }

                def flavors = variant.productFlavors*.name
//                def types = variant.buildType*.name

                def variantName = variant.name.capitalize()
                def newTaskName = "compile${variantName}Aspectj"

                def final String[] srcDirs = ['androidTest', 'test', variant.buildType.name, *flavors].collect {"src/$it/aspectj"};
                def final FileCollection aspects = new SimpleFileCollection(srcDirs.collect { project.file(it) });
                def final FileCollection aptBuildFiles = getAptBuildFilesRoot(project, variant);

                def AspectjCompileTask aspectjCompile = project.task(newTaskName,
                        overwrite: true,
                        group: 'build',
                        description: 'Compiles AspectJ Source',
                        type: AspectjCompileTask) as AspectjCompileTask;

                aspectjCompile.configure {
                    def self = aspectjCompile;

                    self.sourceCompatibility = javaCompile.sourceCompatibility
                    self.targetCompatibility = javaCompile.targetCompatibility
                    self.encoding = javaCompile.options.encoding

                    self.aspectPath = javaCompile.classpath
                    self.destinationDir = javaCompile.destinationDir
                    self.classpath = javaCompile.classpath
                    self.bootClasspath = bootClasspath.join(File.pathSeparator)
                    self.source = javaCompile.source + aspects + aptBuildFiles;

                    //extension params
                    self.binaryWeave = ajParams.binaryWeave;
                    self.binaryExclude = ajParams.binaryExclude;
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
                        def oldDir = javaCompiler.destinationDir;
                        def newDirInfix = hasRetrolambda ? "retrolambda" : "aspectj";
                        def buildSideDir = "$project.buildDir/$newDirInfix/$variant.name";

                        project.logger.warn "set path to inpath weaver for $variant.name with $buildSideDir";
                        if (hasRetrolambda) {
                            addBinaryWeavePath(buildSideDir);
                        } else {
                            javaCompiler.destinationDir = project.file(buildSideDir);

                            addBinaryWeavePath(buildSideDir);
                            project.gradle.taskGraph.afterTask { Task task, TaskState state ->
                                if (task == aspectjCompile) {
                                    // We need to set this back to subsequent android tasks work correctly.
                                    javaCompiler.destinationDir = oldDir;
                                }
                            }
                        }

                        if (!binaryExclude.empty) {
                            binaryExclude.split(",").each {
                                new File(concat(buildSideDir, it as String)).deleteDir();
                            }
                        }
                    }

                    cleanBuildDir(buildPath);
                }

                // uPhyca's fix
                // javaCompile.classpath does not contain exploded-aar/**/jars/*.jars till first run
                javaCompile.doLast {
                    aspectjCompile.classpath = javaCompile.classpath
                }

                def compileAspectTask = project.tasks.getByName(newTaskName) as Task;
                javaCompile.finalizedBy compileAspectTask

                // we should run after every other previous compilers;
                if (hasRetrolambda) {
                    def Task retrolambda = project.tasks["compileRetrolambda$variantName"];
                    retrolambda.dependsOn compileAspectTask;
                }

            }
        }
    }

    def private static cleanBuildDir(def path) {
        def buildDir = new File(path as String);
        if (buildDir.exists()) {
            buildDir.delete();
        }

        buildDir.mkdirs();
    }

    // fix to support Android Pre-processing Tools plugin
    def private static getAptBuildFilesRoot(Project project, variant) {
        def final variantName = variant.name as String;
        def final aptPathShift = "/generated/source/apt/${getSourcePath(variantName)}/";

        return project.files(project.buildDir.path + aptPathShift) as FileCollection;
    }
}
