package com.archinamon.api

import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.utils.findAjSourcesForVariant
import com.archinamon.utils.from
import com.archinamon.utils.logCompilationFinish
import com.archinamon.utils.logCompilationStart
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

internal open class AspectJCompileTask: AbstractCompile() {

    internal class Builder(val project: Project) {

        lateinit var plugin: Plugin<Project>
        lateinit var config: AspectJExtension
        lateinit var javaCompiler: JavaCompile
        lateinit var variantName: String
        lateinit var taskName: String

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

        fun buildAndAttach() {
            val android = AndroidConfig(project)

            val options = mutableMapOf(
                Pair("overwrite", true),
                Pair("group", "build"),
                Pair("description", "Compile .aj source files into java .class with meta instructions"),
                Pair("type", AspectJCompileTask::class.java)
            )

            val task: AspectJCompileTask = project.task(options, taskName) as AspectJCompileTask
            val buildDir = project.file("${project.buildDir}/aspectj/$variantName")

            task.destinationDir = buildDir
            task.aspectJWeaver = AspectJWeaver(project)
            task.aspectJWeaver.ajSources = findAjSourcesForVariant(project, variantName)
            task.source(findAjSourcesForVariant(project, variantName))
            task.classpath = classpath()

            task.aspectJWeaver.targetCompatibility = JavaVersion.VERSION_1_7.toString()
            task.aspectJWeaver.sourceCompatibility = JavaVersion.VERSION_1_7.toString()
            task.aspectJWeaver.destinationDir = buildDir.absolutePath
            task.aspectJWeaver.bootClasspath = android.getBootClasspath().joinToString(separator = File.pathSeparator)
            task.aspectJWeaver.encoding = javaCompiler.options.encoding

            task.aspectJWeaver.compilationLogFile = config.compilationLogFile
            task.aspectJWeaver.addSerialVUID = config.addSerialVersionUID
            task.aspectJWeaver.debugInfo = config.debugInfo
            task.aspectJWeaver.addSerialVUID = config.addSerialVersionUID
            task.aspectJWeaver.noInlineAround = config.noInlineAround
            task.aspectJWeaver.ignoreErrors = config.ignoreErrors
            task.aspectJWeaver.breakOnError = config.breakOnError
            task.aspectJWeaver.experimental = config.experimental
            task.aspectJWeaver.ajcArgs from config.ajcArgs

            // uPhyca's fix
            // javaCompile.classpath does not contain exploded-aar/**/jars/*.jars till first run
            javaCompiler.doLast {
                task.classpath = classpath()
            }

            //apply behavior
            javaCompiler.finalizedBy(task)
        }

        private fun classpath(): FileCollection {
            return SimpleFileCollection(javaCompiler.classpath.files + javaCompiler.destinationDir)
        }
    }

    lateinit var aspectJWeaver: AspectJWeaver

    @TaskAction
    override fun compile() {
        logCompilationStart()

        destinationDir.deleteRecursively()

        println(classpath.joinToString(prefix = "[", postfix = "]") { file -> file.absolutePath })
        aspectJWeaver.classPath = ArrayList(classpath.files)
        aspectJWeaver.doWeave()

        logCompilationFinish()
    }
}