package com.archinamon.utils

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

const val LANG_AJ = "aspectj"
const val LANG_JAVA = "java"

fun getJavaTask(baseVariantData: BaseVariantData): JavaCompile {
    return baseVariantData.taskContainer.javacTask.get()
}

fun getAjSourceAndExcludeFromJavac(project: Project, variantData: BaseVariantData): FileCollection {
    val javaTask = getJavaTask(variantData)

    val flavors: List<String>? = variantData.variantConfiguration.productFlavors.map { flavor -> flavor.name }
    val srcSet = mutableListOf("main", variantData.variantConfiguration!!.buildType!!.name)
    flavors?.let { srcSet.addAll(it) }

    val srcDirs = srcSet.map { "src/$it/aspectj" }
    val aspects: FileCollection = project.layout.files(srcDirs.map { project.file(it) })

    javaTask.exclude { treeElem ->
        treeElem.file in aspects.files
    }

    return aspects.filter(File::exists)
}

fun findAjSourcesForVariant(project: Project, variantName: String): MutableSet<File> {
    return findSourcesForVariant(project, variantName, LANG_AJ)
}

fun findJavaSourcesForVariant(project: Project, variantName: String): MutableSet<File> {
    return findSourcesForVariant(project, variantName, LANG_JAVA)
}

fun findSourcesForVariant(project: Project, variantName: String, language: String): MutableSet<File> {
    val possibleDirs: MutableSet<File> = mutableSetOf()
    if (project.file("src/main/$language").exists()) {
        possibleDirs.add(project.file("src/main/$language"))
    }

    val types = variantName.split("(?=\\p{Upper})".toRegex())
    val root = project.file("src").listFiles()

    root.forEach { file ->
        types.forEach { type ->
            if (file.name.contains(type.toLowerCase()) &&
                    file.list().any { it.contains(language) }) {
                possibleDirs.add(File(file, language))
            }
        }
    }

    return LinkedHashSet(possibleDirs)
}

fun getVariantDataList(plugin: BasePlugin<out BaseExtension2>): List<BaseVariantData> {
    return getVariantScopes(plugin).map(VariantScope::getVariantData)
}

fun getVariantScopes(plugin: BasePlugin<out BaseExtension2>): List<VariantScope> {
    return plugin.variantManager.variantScopes
}

internal infix fun <E> MutableCollection<in E>.shl(elem: E): MutableCollection<in E> {
    this.add(elem)
    return this
}

internal infix fun <E> MutableCollection<in E>.from(elems: Collection<E>) {
    this.addAll(elems)
}