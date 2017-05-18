package com.archinamon.plugin

import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.ConfigScope
import com.archinamon.api.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import javax.inject.Inject

internal sealed class AspectJWrapper(private val scope: ConfigScope): Plugin<Project> {

    internal class Std @Inject constructor(): AspectJWrapper(ConfigScope.STD)
    internal class Ext @Inject constructor(): AspectJWrapper(ConfigScope.EXT)
    internal class Test @Inject constructor(): AspectJWrapper(ConfigScope.TEST) {

        override fun variantFilter(variant: BaseVariantData<out BaseVariantOutputData>): Boolean {
            return variant.type.isForTesting
        }

        override fun mutateClasspath(config: AndroidConfig) {
            if (config.aspectj().extendClasspath) {
                config.project.repositories.mavenCentral()
                config.project.dependencies.add("androidTestCompile", "org.aspectj:aspectjrt:${config.aspectj().ajc}")
            }
        }
    }

    internal lateinit var transformer: AspectJTransform

    override fun apply(project: Project) {
        project.extensions.create("aspectj", AspectJExtension::class.java)

        val config = AndroidConfig(project, scope)

        mutateClasspath(config)
        configProject(this, config)

        if (config.isLibraryPlugin) {
            transformer = LibTransformer(project)
        } else {
            transformer = obtainTransformer(project)
        }

        transformer.withConfig(config)
        config.extAndroid.registerTransform(transformer)
    }

    open fun variantFilter(variant: BaseVariantData<out BaseVariantOutputData>): Boolean {
        return true
    }

    protected open fun mutateClasspath(config: AndroidConfig) {
        if (config.aspectj().extendClasspath) {
            config.project.repositories.mavenCentral()
            config.project.dependencies.add("compile", "org.aspectj:aspectjrt:${config.aspectj().ajc}")
        }
    }

    private fun obtainTransformer(project: Project) = when (scope) {
        ConfigScope.STD -> StdTransformer(project)
        ConfigScope.EXT -> ExtTransformer(project)
        ConfigScope.TEST -> TestTransformer(project)
    }
}