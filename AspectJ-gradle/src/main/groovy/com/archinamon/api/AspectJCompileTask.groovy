package com.archinamon.api

import com.android.build.gradle.BasePlugin
import com.archinamon.AspectJExtension
import org.gradle.api.Project
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile

import static com.archinamon.StatusLogger.logCompilationFinish
import static com.archinamon.StatusLogger.logCompilationStart
import static com.archinamon.VariantUtils.findAjSourcesForVariant

class AspectJCompileTask extends AbstractCompile {

    static class Builder {

        def Project project;
        def BasePlugin plugin;
        def AspectJExtension config;
        def AbstractCompile javaCompiler;
        def variantName;
        def taskName;

        public Builder(Project project) {
            this.project = project;
        }

        def plugin(BasePlugin plugin) {
            this.plugin = plugin;
            this;
        }

        def config(def extension) {
            this.config = extension;
            this;
        }

        def compiler(AbstractCompile compiler) {
            this.javaCompiler = compiler;
            this;
        }

        def variant(def name) {
            this.variantName = name;
            this;
        }

        def name(def name) {
            this.taskName = name;
            this;
        }

        def build() {
            AspectJCompileTask task = project.task(
                this.taskName,
                overwrite: true,
                group: 'build',
                description: 'Compile .aj source files into java .class with meta instructions',
                type: AspectJCompileTask);

            def buildDir = project.file("$project.buildDir/aspectj/$variantName");
            task.destinationDir = buildDir;

            task.configure {
                aspectJWeaver = new AspectJWeaver(project);
                aspectJWeaver.setAjSources(findAjSourcesForVariant(project, variantName))
                task.source(findAjSourcesForVariant(project, variantName));
                task.setClasspath(classpath());

                aspectJWeaver.targetCompatibility = "1.7";
                aspectJWeaver.sourceCompatibility = "1.7";
                aspectJWeaver.destinationDir = buildDir.absolutePath;
                aspectJWeaver.bootClasspath = bootCP();
                aspectJWeaver.encoding = javaCompiler.options.encoding;

                aspectJWeaver.setCompilationLogFile(config.compilationLogFile);
                aspectJWeaver.addSerialVUID = config.addSerialVersionUID;
                aspectJWeaver.debugInfo = config.debugInfo;
                aspectJWeaver.addSerialVUID = config.addSerialVersionUID;
                aspectJWeaver.noInlineAround = config.noInlineAround;
                aspectJWeaver.ignoreErrors = config.ignoreErrors;
                aspectJWeaver.breakOnError = config.breakOnError;
                aspectJWeaver.experimental = config.experimental;
                aspectJWeaver.ajcArgs.addAll config.ajcArgs;
            }

            // uPhyca's fix
            // javaCompile.classpath does not contain exploded-aar/**/jars/*.jars till first run
            javaCompiler.doLast {
                task.setClasspath(classpath());
            }

            //apply behavior
            javaCompiler.finalizedBy task;

            task;
        }

        def private classpath() {
            return new SimpleFileCollection(javaCompiler.classpath.files + javaCompiler.destinationDir);
        }

        def private bootCP() {
            ((plugin.properties['runtimeJarList'] ?: project.android.bootClasspath) as List).join(File.pathSeparator);
        }
    }

    AspectJWeaver aspectJWeaver;

    @Override
    @TaskAction
    protected void compile() {
        logCompilationStart();

        destinationDir.deleteDir();

        println(classpath.toList().toListString());
        aspectJWeaver.setClassPath(classpath.toList());
        aspectJWeaver.doWeave();

        logCompilationFinish();
    }
}