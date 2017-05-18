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
import org.gradle.api.Project

internal fun configProject(wrapper: AspectJWrapper, config: AndroidConfig) {
    checkIfPluginAppliedAfterRetrolambda(config.project)

    config.project.afterEvaluate {
        getVariantDataList(config.plugin)
                .filter(wrapper::variantFilter)
                .forEach { prepareVariant(it, wrapper, config) }

    }

    config.project.gradle.addListener(BuildTimeListener())
}

internal fun prepareVariant(variant: BaseVariantData<out BaseVariantOutputData>, wrapper: AspectJWrapper, config: AndroidConfig) {
    applyVariantSourceSet(variant, config)
    configureCompilerVariant(variant, config)
    configureTransformVariant(variant, wrapper, config)
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

internal fun configureTransformVariant(variant: BaseVariantData<out BaseVariantOutputData>, wrapper: AspectJWrapper, config: AndroidConfig) {
    wrapper.transformer.checkInstantRun(variant)
    getAjSourceAndExcludeFromJavac(config.project, variant)
    wrapper.transformer.apply {
        variantStorage.put(variant.name, variant)
        sourceCompatibility = JavaVersion.VERSION_1_7.toString()
        targetCompatibility = JavaVersion.VERSION_1_7.toString()
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