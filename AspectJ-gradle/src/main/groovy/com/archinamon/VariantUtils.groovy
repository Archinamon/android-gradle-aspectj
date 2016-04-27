package com.archinamon

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.build.gradle.internal.VariantManager
import groovy.transform.CompileStatic
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer

@CompileStatic
def static VariantManager getVariantManager(BasePlugin plugin) {
    return plugin.variantManager;
}

@CompileStatic
def static applyVariantPreserver(NamedDomainObjectContainer<AndroidSourceSet> sets, String dir) {
    String path = getAjPath(dir);
    sets.getByName(dir).java.srcDir(path);
    return path;
}

def static <E extends TestedExtension> DomainObjectSet<? extends BaseVariant> androidVariants(def isLib, E android) {
    isLib ? android.libraryVariants : android.applicationVariants;
}

@CompileStatic
def static <E extends TestedExtension> DomainObjectSet<? extends TestVariant> testVariants(E android) {
    android.testVariants;
}

@CompileStatic
def static <E extends TestedExtension> DomainObjectSet<? extends UnitTestVariant> unitTestVariants(E android) {
    android.unitTestVariants;
}

def static findVarData(def variantData, def variant) {
    variantData.name.equals(variant.name);
}

@CompileStatic
def private static getAjPath(String dir) {
    return "src/$dir/aspectj";
}