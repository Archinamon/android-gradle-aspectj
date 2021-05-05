package com.archinamon.utils

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.scope.TaskContainer
import com.android.build.gradle.internal.variant.BaseVariantData
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties

const val LANG_AJ = "aspectj"
const val LANG_JAVA = "java"

fun getJavaTask(baseVariantData: BaseVariantData): JavaCompile {

    /**
     *  Supporting gradle api 3.1.+
     */
    val variantJavacTaskProp = baseVariantData::class.memberProperties.find { prop ->
        prop.name == "javacTask" && prop.returnType.classifier == JavaCompile::class
    }

    variantJavacTaskProp?.let {
        return variantJavacTaskProp.call(baseVariantData) as JavaCompile
    }

    /**
     *  Supporting gradle api 3.2.+
     */
    val taskContainerFunc = baseVariantData::class.functions.find { func ->
        func.name == "getTaskContainer"
    }
    taskContainerFunc?.let {
        val containerResult = taskContainerFunc.call(baseVariantData) as TaskContainer
        val javacTaskProp = containerResult::class.memberProperties.find { prop ->
            prop.name == "javacTask"
        }

        if (javacTaskProp?.returnType?.classifier == JavaCompile::class) {
            return javacTaskProp.call(containerResult) as JavaCompile
        }
    }

    /**
     *  Supporting gradle api 3.3.+ by default
     */
    return baseVariantData.taskContainer.javacTask.get()
}

fun getAjSourceAndExcludeFromJavac(project: Project, variant: VariantImpl): FileCollection {
    val javaTask = getJavaTask(variant.variantData)

    val flavors: List<String>? = variant.productFlavors.map { flavor -> flavor.second }
    val srcSet = mutableListOf("main", variant.buildType ?: variant.flavorName)
    flavors?.let { srcSet.addAll(it) }

    val srcDirs = srcSet.map { "src/$it/aspectj" }
    val aspects: FileCollection = project.files(srcDirs.map(project::file))

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

fun getVariantDataList(plugin: BasePlugin<*, *, *>): List<VariantImpl> {
    return plugin.variantManager.mainComponents.map {
        it.variant
    }
}

internal infix fun <E> MutableCollection<in E>.shl(elem: E): MutableCollection<in E> {
    this.add(elem)
    return this
}

internal infix fun <E> MutableCollection<in E>.from(elems: Collection<E>) {
    this.addAll(elems)
}