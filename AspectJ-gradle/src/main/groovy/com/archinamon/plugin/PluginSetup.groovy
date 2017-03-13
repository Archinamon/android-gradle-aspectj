package com.archinamon.plugin;

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.api.AspectJCompileTask
import com.archinamon.api.BuildTimeListener
import com.archinamon.utils.VariantUtils
import com.sun.org.apache.xalan.internal.xsltc.compiler.CompilerException
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile

def static configProject(Project project, AndroidConfig config, AspectJExtension settings) {
    project.repositories { project.repositories.mavenCentral(); }
    project.dependencies { compile "org.aspectj:aspectjrt:$settings.ajc"; }
    project.afterEvaluate {
        prepareVariant(config);
        configureCompiler(project, config);
    }

    project.gradle.addListener(new BuildTimeListener());

    checkIfPluginAppliedAfterRetrolambda(project);
}

private static void prepareVariant(AndroidConfig config) {
    def sets = config.extAndroid.sourceSets;

    final def Closure applier = {
        String path = "src/$it/aspectj";
        sets.getByName(it).java.srcDir(path);
    }

    // general sets
    ['main', 'test', 'androidTest'].each {
        sets.getByName(it).java.srcDir("src/$it/aspectj");
    }

    // applies srcSet 'aspectj' for each build variant
    VariantUtils.getVariantDataList(config.plugin).each { BaseVariantData<? extends BaseVariantOutputData> variant ->
        variant.variantConfiguration.productFlavors*.name.each(applier);
        variant.variantConfiguration.buildType*.name.each(applier);
    }
}

private static void configureCompiler(Project project, AndroidConfig config) {
    VariantUtils.getVariantDataList(config.plugin).each { BaseVariantData<? extends BaseVariantOutputData> variant ->
        def variantName = variant.name.capitalize();
        def taskName = "compile${variantName}AspectJ";

        new AspectJCompileTask.Builder(project)
            .plugin(project.plugins.getPlugin(config.isLibraryPlugin ? LibraryPlugin : AppPlugin))
            .config(project.extensions.getByType(AspectJExtension))
            .compiler(VariantUtils.getJavaTask(variant))
            .variant(variant.name)
            .name(taskName)
            .build();
    }
}

private static void checkIfPluginAppliedAfterRetrolambda(final Project project) {
    boolean appears = project.plugins.findPlugin(AndroidConfig.RETROLAMBDA);
    if (!appears) {
        project.afterEvaluate {
            //RL was defined before AJ plugin
            if (!appears && project.plugins.findPlugin(AndroidConfig.RETROLAMBDA)) {
                throw new GradleException(AndroidConfig.MISDEFINITION);
            }
        }
    }
}