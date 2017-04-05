package com.archinamon.utils

import com.android.build.api.transform.JarInput
import com.archinamon.api.BuildPolicy
import java.io.File

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
    println("Ignoring additional jars adding to -inpath in simple mode")
}

internal fun logJarInpathAdded(jar: JarInput) {
    println("include jar :: ${jar.file.absolutePath}")
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

internal fun logBuildParametersAdapted(args: MutableList<String?>, logfile: String) {
    var params: String = ""

    args.forEach { params += if (it?.startsWith('-')!!) "$it :: " else ( if (it.length > 200) "[ list files ],\n" else "$it, ") }

    println("Ajc config: $params")
    println("Detailed log in $logfile")
}
