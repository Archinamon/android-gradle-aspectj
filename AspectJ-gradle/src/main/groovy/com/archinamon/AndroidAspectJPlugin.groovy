package com.archinamon

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
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
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile

class AndroidAspectJPlugin implements Plugin<Project> {

    def private static final BIN_INCLUDE_EXC = "You should correctly specify module name for :aspectj:binaryInclude parameter, " +
            "i.e. com.android.support:multidex:1.0.1, where 'com.android.support' is a module group and 'multidex' is a module name.";

    def private static isLibraryPlugin = false;

    @Override
    void apply(Project project) {
        final def plugin;
        final AndroidAspectJExtension params;

        if (project.plugins.hasPlugin(AppPlugin)) {
            plugin = project.plugins.getPlugin(AppPlugin);
        } else if (project.plugins.hasPlugin(LibraryPlugin)) {
            plugin = project.plugins.getPlugin(LibraryPlugin);
            isLibraryPlugin = true;
        } else {
            throw new GradleException('You must apply the Android plugin or the Android library plugin')
        }

        params = project.extensions.create('aspectj', AndroidAspectJExtension);

        getVariants(project).all { BaseVariant variant ->
            final def sets = project.android.sourceSets;
            final def Closure applier = { String name ->
                applyVariantPreserver(sets, name);
            }

            variant.productFlavors*.name.each(applier);
            variant.buildType*.name.each(applier);
        }

        project.android.sourceSets {
            main.java.srcDir('src/main/aspectj');
            androidTest.java.srcDir('src/androidTest/aspectj');
            test.java.srcDir('src/test/aspectj');
        }

        project.repositories { mavenCentral() }
        project.repositories { maven { url 'http://repo.spring.io/snapshot/' } }
        project.dependencies { compile "org.aspectj:aspectjrt:1.8.9.BUILD-SNAPSHOT" }
        project.afterEvaluate {
            final def hasRetrolambda = project.plugins.hasPlugin('me.tatarka.retrolambda') as boolean;
            final VariantManager manager = getVariantManager(plugin as BasePlugin);

            getVariants(project).all { BaseVariant variant ->
                BaseVariantData<? extends BaseVariantOutputData> data = manager.variantDataList.find { findVarData(it, variant); }

                AbstractCompile javaCompiler = variant.javaCompiler
                if (!javaCompiler instanceof JavaCompile)
                    throw new CompilerException("AspectJ plugin doesn't support other java-compilers, only javac");

                final def JavaCompile javaCompile = (JavaCompile) javaCompiler;

                def bootClasspath
                if (plugin.properties['runtimeJarList']) {
                    bootClasspath = plugin.runtimeJarList
                } else {
                    bootClasspath = project.android.bootClasspath
                }

                def flavors = variant.productFlavors*.name
                def types = variant.buildType*.name

                def variantName = variant.name.capitalize()
                def newTaskName = "compile${variantName}Aspectj"

                def final String[] srcDirs = ['androidTest', variant.buildType.name, *flavors].collect {"src/$it/aspectj"};
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
                    self.binaryWeave = params.binaryWeave;
                    self.binaryExclude = params.binaryExclude;
                    self.binaryInclude = params.binaryInclude;
                    self.logFile = params.logFileName;
                    self.weaveInfo = params.weaveInfo;
                    self.ignoreErrors = params.ignoreErrors;
                    self.addSerialVUID = params.addSerialVersionUID;
                    self.interruptOnWarnings = params.interruptOnWarnings;
                    self.interruptOnErrors = params.interruptOnErrors;
                    self.interruptOnFails = params.interruptOnFails;
                }

                aspectjCompile.doFirst {
                    def final buildPath = data.scope.javaOutputDir.absolutePath;

                    if (binaryWeave) {
                        def oldDir = javaCompiler.destinationDir;
                        def newDirInfix = hasRetrolambda ? "retrolambda" : "aspectj";
                        def buildSideDir = "$project.buildDir/$newDirInfix/$variant.name";

                        if (hasRetrolambda) {
                            addBinaryWeavePath(buildSideDir);
                            project.logger.warn "set path to inpath weaver for $variant.name with $buildSideDir";
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
                            binaryExclude.each {
                                new File(concat(buildSideDir, it as String)).deleteDir();
                            }
                        }

                        // we should parse string name of modules and find them in /exploded-aars
                        if (!binaryInclude.empty) {
                            binaryInclude.each {
                                String[] module = (it as String).split(':');
                                project.logger.warn it as String;
                                if (module.length < 2) throw new StopExecutionException(BIN_INCLUDE_EXC);

                                def moduleGroup = module[0];
                                def moduleName = module[1];
                                def moduleVersion = module.length == 3 ? "/${module[2]}" : "";

                                def moduleFile = "$project.buildDir/intermediates/exploded-aar/$moduleGroup$moduleName/$moduleVersion/jars/classes.jar";
                                addBinaryWeavePath(moduleFile);
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
                // we should run after every other previous compilers;
                if (hasRetrolambda) {
                    def Task retrolambda = project.tasks["compileRetrolambda$variantName"];
                    retrolambda.dependsOn compileAspectTask;
                }

                //apply behavior
                project.tasks["compile${variantName}Ndk"].dependsOn compileAspectTask;
            }
        }
    }

    private static VariantManager getVariantManager(BasePlugin plugin) {
        return plugin.variantManager;
    }

    def private static concat(String buildPath, String _package) {
        String strPath = _package.replace(".", File.separator);
        return(buildPath + "/$strPath");
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

        // project.logger.warn(aptPathShift);
        return project.files(project.buildDir.path + aptPathShift) as FileCollection;
    }

    def private static getSourcePath(String variantName) {
        def String[] types = variantName.split("(?=\\p{Upper})");
        if (types.length > 0 && types.length < 3) {
            def additionalPathShift = "";
            types.each { String type -> additionalPathShift += "${type.toLowerCase()}/" }
            return additionalPathShift;
        } else if (types.length > 2) {
            def buildType = types.last().toLowerCase();
            def String flavor = "";
            types.eachWithIndex { elem, idx -> if (idx != types.length - 1) flavor += elem.toLowerCase(); };
            return "$flavor/$buildType";
        } else {
            return variantName;
        }
    }

    def private static applyVariantPreserver(def sets, String dir) {
        String path = getAjPath(dir);
        sets.getByName(dir).java.srcDir(path);
        return path;

    }

    def static DefaultDomainObjectSet<? extends BaseVariant> getVariants(Project project) {
        isLibraryPlugin ? project.android.libraryVariants : project.android.applicationVariants;
    }

    def static getAjPath(String dir) {
        return "src/$dir/aspectj";
    }

    def private static findVarData(def variantData, def variant) {
        return variantData.name.equals(variant.name);
    }
}
