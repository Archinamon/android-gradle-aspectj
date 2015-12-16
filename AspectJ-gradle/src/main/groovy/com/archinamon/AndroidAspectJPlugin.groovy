// This plugin is based on https://github.com/JakeWharton/hugo
package com.archinamon;

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import com.sun.org.apache.xalan.internal.xsltc.compiler.CompilerException
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile

class AndroidAspectJPlugin implements Plugin<Project> {

    private static def isLibraryPlugin = false;

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

        params = project.extensions.findByType(AndroidAspectJExtension) ?:
                project.extensions.create('aspectj', AndroidAspectJExtension);

        getVariants(project).all {
            final def sets = project.android.sourceSets;
            final def Closure applier = { applyVariantPreserver(sets, it); }
            it.productFlavors*.name.each(applier);
            it.buildType*.name.each(applier);
        }

        project.android.sourceSets {
            main.java.srcDir('src/main/aspectj');
            androidTest.java.srcDir('src/androidTest/aspectj');
            test.java.srcDir('src/test/aspectj');
        }
        project.repositories { mavenCentral() }
        project.logger.info "ajc version: $params.ajcVersion";
        project.dependencies { compile "org.aspectj:aspectjrt:$params.ajcVersion" }
        project.afterEvaluate {
            final def hasRetrolambda = project.plugins.hasPlugin('me.tatarka.retrolambda') as boolean;

            getVariants(project).all { BaseVariant variant ->
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

                def final String[] srcDirs = ['androidTest', *flavors, *types].collect {"src/$it/aspectj"};
                def final FileCollection aspects = new SimpleFileCollection(srcDirs.collect { project.file(it) });
                def final FileCollection aptBuildFiles = getAptBuildFilesRoot(project, variant);

                def File file = new File(project.buildDir, "ajc_dirs.log");
                variant.variantData.extraGeneratedSourceFolders.each {
                    file << it as String;
                    file << "\n";
                }

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
                    self.logFile = params.logFileName;
                    self.weaveInfo = params.weaveInfo;
                    self.ignoreErrors = params.ignoreErrors;
                    self.addSerialVUID = params.addSerialVersionUID;
                }

                aspectjCompile.doFirst {
                    if (javaCompile.destinationDir.exists()) {
                        javaCompile.destinationDir.deleteDir()
                    }

                    javaCompile.destinationDir.mkdirs()
                }

                // uPhyca's fix
                // javaCompile.classpath does not contain exploded-aar/**/jars/*.jars till first run
                javaCompile.doLast {
                    aspectjCompile.classpath = javaCompile.classpath
                }

                def compileAspect = project.tasks.getByName(newTaskName) as Task;

                // fix to support Retrolambda plugin
                if (hasRetrolambda) {
                    def Task retrolambdaTask = project.tasks["compileRetrolambda$variantName"];
                    retrolambdaTask.dependsOn(compileAspect);
                } else {
                    variant.javaCompiler.finalizedBy(compileAspect);
                }
            }
        }
    }

    // fix to support Android Pre-processing Tools plugin
    private static def FileCollection getAptBuildFilesRoot(Project project, variant) {
        def final aptPathShift;
        def final variantName = variant.name as String;
        def String[] types = variantName.split("(?=\\p{Upper})");
        if (types.length > 0 && types.length < 3) {
            def additionalPathShift = "";
            types.each { String type -> additionalPathShift += type.toLowerCase() + "/" };
            aptPathShift = "/generated/source/apt/$additionalPathShift";
        } else if (types.length > 2) {
            def buildType = types.last().toLowerCase();
            def String flavor = "";
            types.eachWithIndex { elem, idx -> if (idx != types.length - 1) flavor += elem.toLowerCase(); };
            aptPathShift = "/generated/source/apt/$flavor/$buildType";
        } else {
            aptPathShift = "/generated/source/apt/$variantName";
        }

        // project.logger.warn(aptPathShift);
        return project.files(project.buildDir.path + aptPathShift) as FileCollection;
    }

    private static def String applyVariantPreserver(sets, String dir) {
        String path = getAjPath(dir);
        sets.getByName(dir).java.srcDir(path);
        return path;

    }

    static def DefaultDomainObjectSet<? extends BaseVariant> getVariants(Project project) {
        isLibraryPlugin ? project.android.libraryVariants : project.android.applicationVariants;
    }

    static def String getAjPath(String dir) {
        return "src/$dir/aspectj";
    }
}
