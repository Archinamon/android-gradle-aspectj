package com.archinamon.utils

import com.android.build.api.transform.JarInput
import com.archinamon.api.transform.BuildPolicy
import java.io.File

internal fun logBypassTransformation() {
    println("---------- AspectJ tasks bypassed with no outputs ----------")
}

internal fun logCompilationStart() {
    println("---------- Starting AspectJ sources compilation ----------")
}

internal fun logCompilationFinish() {
    println("---------- Finish AspectJ compiler ----------")
}

internal fun logAugmentationStart() {
    println("---------- Starting augmentation with AspectJ transformer ----------")
}

internal fun logAugmentationFinish() {
    println("---------- Finish AspectJ transformer ----------")
}

internal fun logNoAugmentation() {
    println("---------- Exit AspectJ transformer w/o processing ----------")
}

internal fun logEnvInvalid() {
    println("Ajc classpath doesn't has needed runtime environment")
}

internal fun logWeaverBuildPolicy(policy: BuildPolicy) {
    println("Weaving in ${policy.name.toLowerCase()} mode")
}

internal fun logIgnoreInpathJars() {
    println("Ignoring include/exclude option of -inpath parameter in simple mode.\n" +
            "Switch to `aspectj-ext` plugin to enable this behavior!")
}

internal fun logJarInpathAdded(jar: JarInput) {
    println("include jar :: ${jar.file.absolutePath}")
}

internal fun logJarInpathRemoved(jar: JarInput) {
    println("exclude jar :: ${jar.file.absolutePath}")
}

internal fun logJarAspectAdded(jar: JarInput) {
    println("include aspects from :: ${jar.file.absolutePath}")
}

internal fun logJarAspectAdded(file: File) {
    println("include aspects from :: ${file.absolutePath}")
}

internal fun logExtraAjcArgumentAlreadyExists(arg: String) {
    println("extra AjC argument $arg already exists in build config")
}

internal fun logBuildParametersAdapted(args: MutableCollection<String?>, logfile: String) {
    fun extractParamsToString(it: String): String {
        return when {
            it.startsWith('-') -> "$it :: "
            else -> when {
                it.length > 200 -> "[ list files ],\n"
                else -> "$it, "
            }
        }
    }

    val params = args
            .filterNotNull()
            .joinToString(transform = ::extractParamsToString)

    println("Ajc config: $params")
    println("Detailed log in $logfile")
}
