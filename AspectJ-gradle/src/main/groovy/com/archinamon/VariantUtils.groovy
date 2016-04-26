package com.archinamon

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.VariantManager
import org.gradle.api.internal.DefaultDomainObjectSet

def static VariantManager getVariantManager(BasePlugin plugin) {
    return plugin.variantManager;
}

def static applyVariantPreserver(def sets, String dir) {
    String path = getAjPath(dir);
    sets.getByName(dir).java.srcDir(path);
    return path;
}

def static DefaultDomainObjectSet<? extends BaseVariant> getVariants(def isLib, def android) {
    DefaultDomainObjectSet<? extends BaseVariant> variants = isLib ? android.libraryVariants : android.applicationVariants;
    variants.addAll(getTestVariants(android));
    return variants;
}

def static DefaultDomainObjectSet<? extends BaseVariant> getTestVariants(def android) {
    android.testVariants;
}

def static findVarData(def variantData, def variant) {
    variantData.name.equals(variant.name);
}

def private static getAjPath(String dir) {
    return "src/$dir/aspectj";
}