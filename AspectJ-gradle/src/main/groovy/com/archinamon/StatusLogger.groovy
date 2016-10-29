package com.archinamon

import com.android.build.api.transform.JarInput

def public static logAugmentationStart() {
    println "---------- Starting augmentation with AspectJ compiler ----------";
}

def public static logAugmentationFinish() {
    println "---------- Finish AspectJ transformer ----------";
}

def public static logJarInpathAdded(JarInput jar) {
    println "include jar :: $jar.file.absolutePath";
}

def public static logJarAspectAdded(JarInput jar) {
    println "include aspects from :: $jar.file.absolutePath";
}