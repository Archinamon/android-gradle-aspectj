package com.archinamon.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.api.AspectJAppTransform
import com.archinamon.api.AspectJCompileTask
import com.archinamon.api.AspectJLibTransform
import com.archinamon.api.BuildTimeListener
import com.archinamon.utils.VariantUtils
import com.sun.org.apache.xalan.internal.xsltc.compiler.CompilerException
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile

class AndroidAspectJExt implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def config = new AndroidConfig(project);
        def settings = project.extensions.create('aspectj', AspectJExtension);

        PluginSetup.configProject(project, config, settings);

        final TestedExtension module;
        final AspectJAppTransform transformer;
        if (config.isLibraryPlugin) {
            transformer = new AspectJLibTransform(project)
                .withPolicy(AspectJAppTransform.BuildPolicy.LIBRARY);

            module = project.extensions.getByType(LibraryExtension);
        } else {
            transformer = new AspectJAppTransform(project)
                .withPolicy(AspectJAppTransform.BuildPolicy.COMPLEX);

            module = project.extensions.getByType(AppExtension);
        }

        module.registerTransform(transformer
            .withConfig(config)
            .withExtension(settings)
            .prepareProject()
        );
    }
}
