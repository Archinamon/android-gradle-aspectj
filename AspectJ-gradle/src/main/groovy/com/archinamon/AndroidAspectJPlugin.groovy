package com.archinamon;

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.archinamon.api.AspectTransform
import com.archinamon.api.BuildTimeListener
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidAspectJPlugin implements Plugin<Project> {

    private Project project;
    private AndroidConfig config;

    @Override
    void apply(Project project) {
        this.project = project;
        this.config = new AndroidConfig(project);
        def settings = project.extensions.create('aspectj', AspectJExtension);

        project.repositories { project.repositories.mavenCentral() }
        project.dependencies { compile "org.aspectj:aspectjrt:$settings.ajc" }

        prepareVariant(config.extAndroid.sourceSets);

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
}
