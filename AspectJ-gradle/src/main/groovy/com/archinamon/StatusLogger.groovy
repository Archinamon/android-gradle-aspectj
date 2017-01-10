package com.archinamon

import com.android.build.api.transform.JarInput

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