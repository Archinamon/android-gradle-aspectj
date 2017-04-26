package com.archinamon.plugin

import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.MISDEFINITION
import com.archinamon.RETROLAMBDA
import com.archinamon.api.AspectJCompileTask
import com.archinamon.api.BuildTimeListener
import com.archinamon.utils.getAjSourceAndExcludeFromJavac
import com.archinamon.utils.getJavaTask
import com.archinamon.utils.getVariantDataList
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import javax.inject.Inject

internal sealed class AspectJPlugin(private val scope: ConfigScope) : Plugin<Project> {

    internal companion object {
        const val CONFIG_STD = "std"
        const val CONFIG_EXT = "ext"
        const val CONFIG_TEST = "test"
    }

    /** implementations */
    internal class Std @Inject constructor() : AspectJPlugin(ConfigScope.STD)

    internal class Ext @Inject constructor() : AspectJPlugin(ConfigScope.EXT)

    internal class Test @Inject constructor() : AspectJPlugin(ConfigScope.TEST) {
        override fun extendClasspath(config: AndroidConfig, settings: AspectJExtension) {
            if (settings.extendClasspath) {
                config.project.repositories.mavenCentral()
                config.project.dependencies.add("androidTestCompile", "org.aspectj:aspectjrt:${settings.ajc}")
            }
        }

        override fun variantFilter(variant: BaseVariantData<out BaseVariantOutputData>): Boolean {
            return variant.type.isForTesting
        }

    }

    /** base */
    internal fun configurePlugin(config: AndroidConfig, settings: AspectJExtension) {
        checkIfPluginAppliedAfterRetrolambda(config)
        extendClasspath(config, settings)

        config.project.afterEvaluate {
            getVariantDataList(config.plugin)
                    .filter { variantFilter(it) }
                    .forEach { prepareVariant(it, config) }

        }

        config.project.gradle.addListener(BuildTimeListener())

    }

    open fun variantFilter(variant: BaseVariantData<out BaseVariantOutputData>): Boolean {
        return true
    }

    open fun extendClasspath(config: AndroidConfig, settings: AspectJExtension) {
        if (settings.extendClasspath) {
            config.project.repositories.mavenCentral()
            config.project.dependencies.add("compile", "org.aspectj:aspectjrt:${settings.ajc}")
        }
    }

    internal fun prepareVariant(variant: BaseVariantData<out BaseVariantOutputData>, config: AndroidConfig) {
        applyVariantSourceSet(variant, config)
        configureCompilerVariant(variant, config)
        configureTransformVariant(variant, config)
    }

    internal fun configureTransformVariant(variant: BaseVariantData<out BaseVariantOutputData>, config: AndroidConfig) {
        config.transform.checkInstantRun(variant)
        getAjSourceAndExcludeFromJavac(config.project, variant)
        config.transform.apply {
            variantStorage.put(variant.name, variant)
            sourceCompatibility = JavaVersion.VERSION_1_7.toString()
            targetCompatibility = JavaVersion.VERSION_1_7.toString()
        }
    }

    internal fun applyVariantSourceSet(variant: BaseVariantData<out BaseVariantOutputData>, config: AndroidConfig) {
        val sets = config.extAndroid.sourceSets
        fun applier(path: String) = sets.getByName(path).java.srcDir("src/$path/aspectj")

        // general sets
        arrayOf("main", "test", "androidTest").forEach { it.let(::applier) }

        variant.variantConfiguration.apply {
            productFlavors.forEach { it.name.let(::applier) }
            buildType.name.let(::applier)
        }
    }

    internal fun configureCompilerVariant(variant: BaseVariantData<out BaseVariantOutputData>, config: AndroidConfig) {
        AspectJCompileTask.Builder(config.project)
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

    override fun apply(project: Project) {
        val settings = project.extensions.create("aspectj", AspectJExtension::class.java)
        val config = AndroidConfig(project, scope)

        configurePlugin(config, settings)
    }
}