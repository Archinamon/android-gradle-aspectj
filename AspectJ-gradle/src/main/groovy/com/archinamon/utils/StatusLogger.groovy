package com.archinamon.utils

import com.android.build.api.transform.JarInput
import com.archinamon.api.AspectJAppTransform.BuildPolicy

def public static logCompilationStart() {
    println "---------- Starting AspectJ sources compilation ----------";
}

def public static logCompilationFinish() {
    println "---------- Finish AspectJ compiler ----------";
}

def public static logAugmentationStart() {
    println "---------- Starting augmentation with AspectJ compiler ----------";
}

def public static logAugmentationFinish() {
    println "---------- Finish AspectJ transformer ----------";
}

def public static logNoAugmentation() {
    println "---------- Exit AspectJ transformer w/o processing ----------";
}

def public static logEnvInvalid() {
    println "Ajc classpath doesn't has needed runtime environment";
}

def public static logWeaverBuildPolicy(BuildPolicy policy) {
    println "Weaving in ${policy.name().toLowerCase()} mode";
}

def public static logIgnoreInpathJars() {
    println "Ignoring additional jars adding to -inpath in simple mode";
}

def public static logJarInpathAdded(JarInput jar) {
    println "include jar :: $jar.file.absolutePath";
}

def public static logJarAspectAdded(JarInput jar) {
    println "include aspects from :: $jar.file.absolutePath";
}

def public static logExtraAjcArgumentAlreayExists(String arg) {
    println "extra AjC argument $arg already exists in build config";
}

def public static logBuildParametersAdapted(String[] args, String logfile) {
    def params = "";
    args.each { params += it.startsWith('-') ? "$it :: " : (it.length() > 200 ? "[ list files ],\n" : "$it, ") }
    println "Ajc config: $params";
    println "Detailed log in $logfile";
}