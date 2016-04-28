package com.archinamon

import groovy.transform.CompileStatic
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection

@CompileStatic
def static getSourcePath(String variantName) {
    def String[] types = variantName.split("(?=\\p{Upper})");
    if (types.length > 0 && types.length < 3) {
        def additionalPathShift = "";
        types.each { String type -> additionalPathShift += "${type.toLowerCase()}/" }
        return additionalPathShift;
    } else if (types.length > 2) {
        def buildType = types.last().toLowerCase();
        def String flavor = "";
        types.eachWithIndex { String elem, int idx -> if (idx != types.length - 1) flavor += elem.toLowerCase(); };
        return "$flavor/$buildType";
    } else {
        return variantName;
    }
}

@CompileStatic
def static concat(String buildPath, String _package) {
    String strPath = _package.replace(".", File.separator);
    return(buildPath + "/$strPath");
}

@CompileStatic
def static setupAspectPath(FileCollection javaTaskClassPath, FileCollection aspects, def isTestVariant = false) {
    def files = new SimpleFileCollection(javaTaskClassPath.findAll { File file ->
        !file.absolutePath.contains("intermediates/classes");
    });

    if (isTestVariant) files.add(aspects);
}