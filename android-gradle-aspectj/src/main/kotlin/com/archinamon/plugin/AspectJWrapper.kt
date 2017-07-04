package com.archinamon.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.api.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import javax.inject.Inject

internal sealed class AspectJWrapper(private val scope: ConfigScope): Plugin<Project> {

    internal companion object {
        const val CONFIG_STD   = "std"
        const val CONFIG_EXT   = "ext"
        const val CONFIG_TEST  = "tst"
    }

    internal class Std @Inject constructor(): AspectJWrapper(ConfigScope.STD) {
        override fun getTransformer(project: Project): AspectJTransform = StdTransformer(project)
    }

    internal class Ext @Inject constructor(): AspectJWrapper(ConfigScope.EXT) {
        override fun getTransformer(project: Project): AspectJTransform = ExtTransformer(project)
    }

    internal class Test @Inject constructor(): AspectJWrapper(ConfigScope.TEST) {
        override fun getTransformer(project: Project): AspectJTransform = TstTransformer(project)
    }

    override fun apply(project: Project) {
        val config = AndroidConfig(project, scope)
        val settings = project.extensions.create("aspectj", AspectJExtension::class.java)

        configProject(project, config, settings)

        val module: TestedExtension
        val transformer: AspectJTransform
        if (config.isLibraryPlugin) {
            transformer = LibTransformer(project)
            module = project.extensions.getByType(LibraryExtension::class.java)
        } else {
            transformer = getTransformer(project)
            module = project.extensions.getByType(AppExtension::class.java)
        }

        transformer.withConfig(config).prepareProject()
        module.registerTransform(transformer)
    }

    internal abstract fun getTransformer(project: Project): AspectJTransform
}