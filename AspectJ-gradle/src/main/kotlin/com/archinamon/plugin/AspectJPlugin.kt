package com.archinamon.plugin

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
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

        override fun extendClasspath(config: AndroidConfig, settings: AspectJExtension) {
            if (settings.extendClasspath) {
                config.project.repositories.mavenCentral()
                config.project.dependencies.add("androidTestCompile", "org.aspectj:aspectjrt:${settings.ajc}")
            }
        }

        override fun prepareVariants(config: AndroidConfig, transform: AspectJTransform) {
            getVariantDataList(config.plugin)
                    .filter { it.type.isForTesting }
                    .forEach {
                        applyVariantSourceSet(it, config)
                        configureCompilerVariant(it, config)
                        transform.setupVariant(it)
                    }
        }
    }

    /** base */
    internal fun configurePlugin(config: AndroidConfig, settings: AspectJExtension, transform: AspectJTransform) {
        checkIfPluginAppliedAfterRetrolambda(config)
        extendClasspath(config, settings)
        transform.registerTransform()

        config.project.afterEvaluate {
            prepareVariants(config, transform)
        }

        config.project.gradle.addListener(BuildTimeListener())

    }

    open fun extendClasspath(config: AndroidConfig, settings: AspectJExtension) {
        if (settings.extendClasspath) {
            config.project.repositories.mavenCentral()
            config.project.dependencies.add("compile", "org.aspectj:aspectjrt:${settings.ajc}")
        }
    }

    open fun prepareVariants(config: AndroidConfig, transform: AspectJTransform) {
        getVariantDataList(config.plugin).forEach {
            applyVariantSourceSet(it, config)
            configureCompilerVariant(it, config)
            transform.setupVariant(it)
        }
    }

    fun applyVariantSourceSet(variant: BaseVariantData<out BaseVariantOutputData>, config: AndroidConfig) {
        val sets = config.extAndroid.sourceSets
        fun applier(path: String) = sets.getByName(path).java.srcDir("src/$path/aspectj")

        // general sets
        arrayOf("main", "test", "androidTest").forEach { it.let(::applier) }

        variant.variantConfiguration.apply {
            productFlavors.forEach { it.name.let(::applier) }
            buildType.name.let(::applier)
        }
    }

    fun configureCompilerVariant(variant: BaseVariantData<out BaseVariantOutputData>, config: AndroidConfig) {
        AspectJCompileTask.Builder(config.project)
                .plugin(config.project.plugins.getPlugin(config))
                .config(config.project.extensions.getByType(AspectJExtension::class.java))
                .compiler(getJavaTask(variant)!!)
                .variant(variant.name)
                .name("compile${variant.name.capitalize()}AspectJ")
                .buildAndAttach(config)
    }

    private fun checkIfPluginAppliedAfterRetrolambda(config: AndroidConfig) {
        val appears = config.project.plugins.hasPlugin(RETROLAMBDA)
        if (!appears) {
            config.project.afterEvaluate {
                //RL was defined before AJ plugin
                if (!appears && config.project.plugins.hasPlugin(RETROLAMBDA)) {
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
        val settings = project.extensions.create("aspectj", AspectJExtension::class.java)
        val config = AndroidConfig(project, scope)
        val transform: AspectJTransform

        if (config.isLibraryPlugin) {
            transform = LibTransformer(config)
        } else {
            transform = getTransformer(config)
        }

        configurePlugin(config, settings, transform)

    }

    internal abstract fun getTransformer(config: AndroidConfig): AspectJTransform
}