package com.archinamon;

import com.android.build.gradle.*
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * @author archinamon
 */
class AndroidConfig {

    def public static final TAG = "AJC:";
    private static final def PLUGIN_EXCEPTION = "$TAG You must apply the Android plugin or the Android library plugin";

    private final Project project;
    final TestedExtension extAndroid;
    final boolean isLibraryPlugin;
    final BasePlugin plugin;

    AndroidConfig(Project project) {
        this.project = project;

        if (project.plugins.hasPlugin(AppPlugin)) {
            extAndroid = project.extensions.getByType(AppExtension);
            plugin = project.plugins.getPlugin(AppPlugin);
            isLibraryPlugin = false;
        } else if (project.plugins.hasPlugin(LibraryPlugin)) {
            extAndroid = project.extensions.getByType(LibraryExtension);
            plugin = project.plugins.getPlugin(LibraryPlugin);
            isLibraryPlugin = true;
        } else {
            plugin = null;
            extAndroid = null;
            isLibraryPlugin = false;
            throw new GradleException(PLUGIN_EXCEPTION);
        }
    }

    def List<File> getBootClasspath() {
        if (project.android.hasProperty('bootClasspath')) {
            return extAndroid.bootClasspath;
        } else {
            return plugin.properties["runtimeJarList"] as List<File>;
        }
    }
}