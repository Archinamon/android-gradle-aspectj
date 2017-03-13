package com.archinamon.api

import static com.archinamon.utils.Looper.loop;

def static String findPackageNameIfAar(File input) {
    if (!input.absolutePath.contains("build-cache")) return input.absolutePath;

    File f = input;

    loop {
        f = f?.parentFile;
    } until { f?.isDirectory() && (f.listFiles().any { findManifest(it) }) }

    File manifest = f.listFiles().find { findManifest(it) };
    if (manifest != null) {
        def xml = new XmlSlurper().parse(manifest);
        return xml.@package.toString();
    }

    return input.absolutePath;
}

def private static findManifest(File f) {
    f.name.equalsIgnoreCase("androidmanifest.xml");
}