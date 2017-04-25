package com.archinamon.plugin

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.MISDEFINITION
import com.archinamon.RETROLAMBDA
import com.archinamon.api.*
import com.archinamon.utils.getJavaTask
import com.archinamon.utils.getVariantDataList
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginContainer
import javax.inject.Inject

internal sealed class AspectJPlugin(private val scope: ConfigScope) : Plugin<Project> {

    internal companion object {
        const val CONFIG_STD = "std"
        const val CONFIG_EXT = "ext"
        const val CONFIG_TEST = "test"
    }

    /** implementations */
    internal class Std @Inject constructor() : AspectJPlugin(ConfigScope.STD) {
        override fun getTransformer(config: AndroidConfig): AspectJTransform = StdTransformer(config)
    }

    internal class Ext @Inject constructor() : AspectJPlugin(ConfigScope.EXT) {
        override fun getTransformer(config: AndroidConfig): AspectJTransform = ExtTransformer(config)
    }

    internal class Test @Inject constructor() : AspectJPlugin(ConfigScope.TEST) {
        override fun getTransformer(config: AndroidConfig): AspectJTransform = TestTransformer(config)

        override fun extendClasspath(project: Project, settings: AspectJExtension) {
            if (settings.extendClasspath) {
                project.repositories.mavenCentral()
                project.dependencies.add("androidTestCompile", "org.aspectj:aspectjrt:${settings.ajc}")
            }
        }

        override fun configureCompiler(project: Project, config: AndroidConfig) {

        }

        override fun prepareVariant(config: AndroidConfig) {
            getVariantDataList(config.plugin).forEach { variant ->

            }
        }
    }

    /** base */
    internal fun configurePlugin(project: Project, config: AndroidConfig, settings: AspectJExtension) {
        checkIfPluginAppliedAfterRetrolambda(project)
        extendClasspath(project, settings)

        project.afterEvaluate {
            prepareVariant(config)
            configureCompiler(project, config)
        }

        project.gradle.addListener(BuildTimeListener())

    }

    open fun extendClasspath(project: Project, settings: AspectJExtension) {
        if (settings.extendClasspath) {
            project.repositories.mavenCentral()
            project.dependencies.add("compile", "org.aspectj:aspectjrt:${settings.ajc}")
        }
    }

    open fun prepareVariant(config: AndroidConfig) {
        val sets = config.extAndroid.sourceSets

        fun applier(path: String) = sets.getByName(path).java.srcDir("src/$path/aspectj")

        // general sets
        arrayOf("main", "test", "androidTest").forEach {
            sets.getByName(it).java.srcDir("src/$it/aspectj")
        }

        // applies srcSet 'aspectj' for each build variant
        getVariantDataList(config.plugin).forEach { variant ->
            variant.variantConfiguration.productFlavors.forEach { applier(it.name) }
            applier(variant.variantConfiguration.buildType.name)
        }
    }

    open fun configureCompiler(project: Project, config: AndroidConfig) {
        getVariantDataList(config.plugin).forEach { variant ->
            val variantName = variant.name.capitalize()

            val taskName = "compile${variantName}AspectJ"
            AspectJCompileTask.Builder(project)
                    .plugin(project.plugins.getPlugin(config))
                    .config(project.extensions.getByType(AspectJExtension::class.java))
                    .compiler(getJavaTask(variant)!!)
                    .variant(variant.name)
                    .name(taskName)
                    .buildAndAttach(config)

        }
    }

    private fun checkIfPluginAppliedAfterRetrolambda(project: Project) {
        val appears = project.plugins.hasPlugin(RETROLAMBDA)
        if (!appears) {
            project.afterEvaluate {
                //RL was defined before AJ plugin
                if (!appears && project.plugins.hasPlugin(RETROLAMBDA)) {
                    throw GradleException(MISDEFINITION)
                }
            }
        }
    }

    private inline fun <reified T> PluginContainer.getPlugin(config: AndroidConfig): T where T : Plugin<Project> {
        @Suppress("UNCHECKED_CAST")
        val plugin: Class<out T> = (if (config.isLibraryPlugin) LibraryPlugin::class.java else AppPlugin::class.java) as Class<T>
        return getPlugin(plugin)
    }

    override fun apply(project: Project) {
        val config = AndroidConfig(project, scope)
        val settings = project.extensions.create("aspectj", AspectJExtension::class.java)
        val transform: AspectJTransform

        configurePlugin(project, config, settings)

        if (config.isLibraryPlugin) {
            transform = LibTransformer(config)
        } else {
            transform = getTransformer(config)
        }

        transform.prepareProject()

    }

    internal abstract fun getTransformer(config: AndroidConfig): AspectJTransform
}