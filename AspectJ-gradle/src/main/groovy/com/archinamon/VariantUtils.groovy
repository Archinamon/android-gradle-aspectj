package com.archinamon

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.compile.JavaCompile

def static SourceTask getJavaTask(BaseVariantData<BaseVariantOutputData> baseVariantData) {
    if (baseVariantData.metaClass.getMetaProperty('javaCompileTask')) {
        return baseVariantData.javaCompileTask
    } else if (baseVariantData.metaClass.getMetaProperty('javaCompilerTask')) {
        return baseVariantData.javaCompilerTask
    }
    return null
}

def static FileCollection getAjSourceAndExcludeFromJavac(Project project, BaseVariantData<BaseVariantOutputData> variantData) {
    def JavaCompile javaTask = getJavaTask(variantData);

    def flavors = variantData.variantConfiguration.productFlavors*.name;
    def srcSet = ['main', variantData.variantConfiguration.buildType.name, *flavors];

    def final String[] srcDirs = srcSet.collect {"src/$it/aspectj"};
    def final FileCollection aspects = new SimpleFileCollection(srcDirs.collect { project.file(it) });

    javaTask.exclude { treeElem ->
        treeElem.file in aspects.files;
    }

    aspects.filter { file ->
        file.exists();
    };
}

def static List<BaseVariantData<? extends BaseVariantOutputData>> getVariantDataList(BasePlugin plugin) {
    return plugin.variantManager.variantDataList;
}