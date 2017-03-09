package com.archinamon

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.archinamon.api.AspectJCompileTask
import com.archinamon.api.AspectTransform
import com.archinamon.api.BuildTimeListener
import com.sun.org.apache.xalan.internal.xsltc.compiler.CompilerException
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile

class AndroidAspectJPlugin implements Plugin<Project> {

    private static final ASPECTJ_PLUGIN = "com.archinamon.aspectj";
    private static final RETROLAMBDA = "me.tatarka.retrolambda";
    private static final MISDEFINITION = "Illegal definition: $ASPECTJ_PLUGIN should be defined after $RETROLAMBDA plugin";

    private Project project;
    private AndroidConfig config;

    @Override
    void apply(Project project) {
        this.project = project;
        this.config = new AndroidConfig(project);
        def settings = project.extensions.create('aspectj', AspectJExtension);

        project.repositories { project.repositories.mavenCentral(); }
        project.dependencies { compile "org.aspectj:aspectjrt:$settings.ajc"; }
        project.afterEvaluate {
            prepareVariant(config.extAndroid.sourceSets);
            configureCompiler();
        }

        project.gradle.addListener(new BuildTimeListener());
        final AspectTransform transformer = new AspectTransform(project)
                .withConfig(config)
                .withExtension(settings)
                .prepareProject();

        if (config.isLibraryPlugin) {
            LibraryExtension library = project.extensions.getByType(LibraryExtension);
            library.registerTransform(transformer);
        } else {
            AppExtension android = project.extensions.getByType(AppExtension);
            android.registerTransform(transformer);
        }

        checkIfPluginAppliedAfterRetrolambda(project);
    }

    def void prepareVariant(final NamedDomainObjectContainer<AndroidSourceSet> sets) {
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

    def void configureCompiler() {
        VariantUtils.getVariantDataList(config.plugin).each { BaseVariantData<? extends BaseVariantOutputData> variant ->
            def variantName = variant.name.capitalize();
            def taskName = "compile${variantName}AspectJ";

            AbstractCompile javaCompiler = variant.javacTask;
            if (!javaCompiler instanceof JavaCompile)
                throw new CompilerException("AspectJ plugin supports only javac");

            new AspectJCompileTask.Builder(project)
                .plugin(project.plugins.getPlugin(config.isLibraryPlugin ? LibraryPlugin : AppPlugin))
                .config(project.extensions.getByType(AspectJExtension))
                .compiler(javaCompiler)
                .variant(variant.name)
                .name(taskName)
                .build();
        }
    }

    def static checkIfPluginAppliedAfterRetrolambda(final Project project) {
        boolean appears = project.plugins.findPlugin(RETROLAMBDA);
        if (!appears) {
            project.afterEvaluate {
                //RL was defined before AJ plugin
                if (!appears && project.plugins.findPlugin(RETROLAMBDA)) {
                    throw new GradleException(MISDEFINITION);
                }
            }
        }
    }
}
