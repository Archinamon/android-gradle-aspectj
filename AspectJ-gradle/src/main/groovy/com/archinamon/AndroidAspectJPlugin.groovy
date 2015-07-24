// This plugin is based on https://github.com/JakeWharton/hugo
package com.archinamon

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.compile.JavaCompile

class AndroidAspectJPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        final def variants
        final def plugin

        try {
            if (project.plugins.hasPlugin(AppPlugin)) {
                variants = project.android.applicationVariants
                plugin = project.plugins.getPlugin(AppPlugin)
            } else if (project.plugins.hasPlugin(LibraryPlugin)) {
                variants = project.android.libraryVariants
                plugin = project.plugins.getPlugin(LibraryPlugin)
            } else {
                throw new GradleException("The 'com.android.application' or 'com.android.library' plugin is required.")
            }
        } catch (Exception e) {
            throw new GradleException(e.getMessage(), e.getCause());
        }

        project.repositories {
            mavenCentral()
        }
        project.dependencies {
            compile 'org.aspectj:aspectjrt:1.8.+'
        }

        project.afterEvaluate {
            final def hasRetrolambda = project.plugins.hasPlugin('me.tatarka.retrolambda') as boolean;

            variants.all { variant ->
                JavaCompile javaCompile = variant.javaCompile
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

                def AspectjCompileTask aspectjCompile = project.task(newTaskName,
                        overwrite: true,
                        description: 'Compiles AspectJ Source',
                        type: AspectjCompileTask) {} as AspectjCompileTask;

                def final String srcDirs = ['androidTest', *flavors, *types].collect {"src/$it/aspectj"};
                aspectjCompile.doFirst {
                    aspectpath = javaCompile.classpath
                    destinationDir = javaCompile.destinationDir
                    classpath = javaCompile.classpath
                    bootclasspath = bootClasspath.join(File.pathSeparator)
                    sourceroots = javaCompile.source +
                            new SimpleFileCollection(srcDirs.collect {project.file(it)}) +
                            getAptBuildFilesRoot(project, variant).getAsFileTree();

                    project.logger.warn sourceroots.collect {it.absolutePath}.join("; ");

                    if (javaCompile.destinationDir.exists()) {

                        javaCompile.destinationDir.deleteDir()
                    }

                    javaCompile.destinationDir.mkdirs()
                }

                def compileAspect = project.tasks.getByName(newTaskName) as Task;

                // fix to support Retrolambda plugin
                if (hasRetrolambda) {
                    def Task retrolambdaTask = project.tasks["compileRetrolambda$variantName"];
                    retrolambdaTask.dependsOn(compileAspect);
                } else {
                    variant.javaCompile.finalizedBy(compileAspect);
                }
            }
        }

        // Forces Android Studio to recognize AspectJ folder as code
        project.android.sourceSets {
            main.java.srcDir('src/main/aspectj')
            androidTest.java.srcDir('src/androidTest/aspectj')
            test.java.srcDir('src/test/aspectj')
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

        project.logger.warn(aptPathShift);
        return project.files(project.buildDir.path + aptPathShift) as FileCollection;
    }
}