package com.archinamon.api

import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.plugin.ConfigScope
import com.archinamon.utils.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.create
import java.io.File
import java.util.*

internal open class AspectJCompileTask : AbstractCompile() {

    internal class Builder(val project: Project) {

        private lateinit var plugin: Plugin<Project>
        private lateinit var config: AspectJExtension
        private lateinit var javaCompiler: JavaCompile
        private lateinit var variantName: String
        private lateinit var taskName: String
        private var overwrite: Boolean = false

        fun plugin(plugin: Plugin<Project>): Builder {
            this.plugin = plugin
            return this
        }

        fun config(extension: AspectJExtension): Builder {
            this.config = extension
            return this
        }

        fun compiler(compiler: JavaCompile): Builder {
            this.javaCompiler = compiler
            return this
        }

        fun variant(name: String): Builder {
            this.variantName = name
            return this
        }

        fun name(name: String): Builder {
            this.taskName = name
            return this
        }

        fun overwriteJavac(overwrite: Boolean): Builder {
            this.overwrite = overwrite
            return this
        }

        fun buildAndAttach(android: AndroidConfig) {
            val options = mutableMapOf(
                    "name" to taskName,
                    "dependsOn" to javaCompiler.name,
                    "group" to "build",
                    "description" to "Compile .aj source files into java .class with meta instructions",
                    "type" to AspectJCompileTask::class.java
            )

            val sources = findAjSourcesForVariant(project, variantName)
            val task = project.tasks.create(options, closureOf<AspectJCompileTask> task@ {
                compileMode = android.scope
                destinationDir = obtainBuildDirectory(android)
                aspectJWeaver = AspectJWeaver(project)

                classpath = classpath(withJavaCp = true)
                doFirst { classpath += javaCompiler.classpath }

                findCompiledAspectsInClasspath(this@task, config.includeAspectsFromJar)
                source(sources)

                aspectJWeaver.apply {
                    ajSources = sources
                    inPath shl this@task.destinationDir

                    targetCompatibility = config.java.toString()
                    sourceCompatibility = config.java.toString()
                    destinationDir = resolveDestinationDir(this@task)
                    bootClasspath = android.getBootClasspath().joinToString(separator = File.pathSeparator)
                    encoding = javaCompiler.options.encoding

                    compilationLogFile = config.compilationLogFile
                    addSerialVUID = config.addSerialVersionUID
                    debugInfo = config.debugInfo
                    addSerialVUID = config.addSerialVersionUID
                    noInlineAround = config.noInlineAround
                    ignoreErrors = config.ignoreErrors
                    breakOnError = config.breakOnError
                    experimental = config.experimental
                    ajcArgs from config.ajcArgs
                }
            }) as AspectJCompileTask

            if (overwrite) {
                javaCompiler.enabled = false
                task.aspectJWeaver.ajSources
                        .addAll(findJavaSourcesForVariant(project, variantName))
            }

            // javaCompile.classpath does not contain exploded-aar/**/jars/*.jars till first run
            javaCompiler.doLast {
                task.classpath = classpath(withJavaCp = true)
                findCompiledAspectsInClasspath(task, config.includeAspectsFromJar)
            }

            //apply behavior
            javaCompiler.finalizedBy(task)
        }

        private fun resolveDestinationDir(task: AspectJCompileTask) =
                (if (overwrite) javaCompiler.destinationDir else task.destinationDir)
                        .absolutePath

        private fun obtainBuildDirectory(android: AndroidConfig): File? {
            return if (android.scope == ConfigScope.PROVIDE) {
                javaCompiler.destinationDir
            } else {
                project.file("${project.buildDir}/$LANG_AJ/$variantName")
            }
        }

        private fun classpath(withJavaCp: Boolean): FileCollection =
                ClasspathFileCollection(setOf(javaCompiler.destinationDir)).apply {
                    if (withJavaCp) plus(javaCompiler.classpath)
                }

        private fun findCompiledAspectsInClasspath(task: AspectJCompileTask, aspectsFromJar: Collection<String>) {
            val classpath: FileCollection = task.classpath
            val aspects: MutableSet<File> = mutableSetOf()

            classpath.forEach { file ->
                if (aspectsFromJar.isNotEmpty() && DependencyFilter.isIncludeFilterMatched(file, aspectsFromJar)) {
                    logJarAspectAdded(file)
                    aspects shl file
                }
            }

            if (aspects.isNotEmpty()) task.aspectJWeaver.aspectPath from aspects
        }
    }

    @Internal lateinit var compileMode: ConfigScope
    @Internal lateinit var aspectJWeaver: AspectJWeaver

    @TaskAction
    fun compile() {
        logCompilationStart()

        if (compileMode != ConfigScope.PROVIDE) {
            destinationDir.deleteRecursively()
        }

        aspectJWeaver.classPath = LinkedHashSet(classpath.files)
        aspectJWeaver.doWeave()

        logCompilationFinish()
    }
}
