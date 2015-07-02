// This plugin is based on https://github.com/JakeWharton/hugo
package org.fxclub.archinamon

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.Variant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
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

                def variantName = variant.name.capitalize()
                def newTaskName = "compile${variantName}Aspectj"

                def AspectjCompileTask aspectjCompile = project.task(newTaskName,
                                                                     overwrite: true,
                                                                     description: 'Compiles AspectJ ' + 'Source',
                                                                     type: AspectjCompileTask) {} as AspectjCompileTask;

                aspectjCompile.doFirst {
                    aspectpath = javaCompile.classpath
                    destinationDir = javaCompile.destinationDir
                    classpath = javaCompile.classpath
                    bootclasspath = bootClasspath.join(File.pathSeparator)
                    sourceroots = javaCompile.source + getAptBuildFilesRoot(project, variant as Variant).getAsFileTree();

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
    }

    // fix to support Android Pre-processing Tools plugin
    private static def FileCollection getAptBuildFilesRoot(Project project, Variant variant) {
        def final aptPathShift = "/generated/source/apt/${variant.mergedFlavor.name + "/" + variant.buildType}" as String;
        project.logger.info(aptPathShift);
        return project.files(project.buildDir.path + aptPathShift) as FileCollection;
    }
}