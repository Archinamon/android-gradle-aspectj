package com.archinamon

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
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

def static <E extends TestedExtension> DefaultDomainObjectSet<? extends BaseVariant> androidVariants(def isLib, E android) {
    isLib ? android.libraryVariants : android.applicationVariants;
}

def static <E extends TestedExtension> DefaultDomainObjectSet<? extends TestVariant> testVariants(E android) {
    android.testVariants;
}

def static <E extends TestedExtension> DefaultDomainObjectSet<? extends UnitTestVariant> unitTestVariants(E android) {
    android.unitTestVariants;
}

def static findVarData(def variantData, def variant) {
    variantData.name.equals(variant.name);
}

def private static getAjPath(String dir) {
    return "src/$dir/aspectj";
}