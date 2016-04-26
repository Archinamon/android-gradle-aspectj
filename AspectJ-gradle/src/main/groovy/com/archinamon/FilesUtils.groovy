package com.archinamon

def static getSourcePath(String variantName) {
    def String[] types = variantName.split("(?=\\p{Upper})");
    if (types.length > 0 && types.length < 3) {
        def additionalPathShift = "";
        types.each { String type -> additionalPathShift += "${type.toLowerCase()}/" }
        return additionalPathShift;
    } else if (types.length > 2) {
        def buildType = types.last().toLowerCase();
        def String flavor = "";
        types.eachWithIndex { elem, idx -> if (idx != types.length - 1) flavor += elem.toLowerCase(); };
        return "$flavor/$buildType";
    } else {
        return variantName;
    }
}

def static concat(String buildPath, String _package) {
    String strPath = _package.replace(".", File.separator);
    return(buildPath + "/$strPath");
}