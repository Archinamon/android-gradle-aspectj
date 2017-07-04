package com.archinamon

import com.android.build.gradle.*
import com.archinamon.plugin.ConfigScope
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

const private val ASPECTJ_PLUGIN = "com.archinamon.aspectj"
const val RETROLAMBDA = "me.tatarka.retrolambda"
const val MISDEFINITION = "Illegal definition: $ASPECTJ_PLUGIN should be defined after $RETROLAMBDA plugin"

const private val TAG = "AJC:"
const private val PLUGIN_EXCEPTION = "$TAG You must apply the Android plugin or the Android library plugin"

internal class AndroidConfig(val project: Project, val scope: ConfigScope) {

    val extAndroid: BaseExtension
    val isLibraryPlugin: Boolean
    val plugin: BasePlugin

    init {
        if (project.plugins.hasPlugin(AppPlugin::class.java)) {
            extAndroid = project.extensions.getByType(AppExtension::class.java)
            plugin = project.plugins.getPlugin(AppPlugin::class.java)
            isLibraryPlugin = false
        } else if (project.plugins.hasPlugin(LibraryPlugin::class.java)) {
            extAndroid = project.extensions.getByType(LibraryExtension::class.java)
            plugin = project.plugins.getPlugin(LibraryPlugin::class.java)
            isLibraryPlugin = true
        } else if (project.plugins.hasPlugin(TestPlugin::class.java)) {
            extAndroid = project.extensions.getByType(TestExtension::class.java)
            plugin = project.plugins.getPlugin(TestPlugin::class.java)
            isLibraryPlugin = false
        } else {
            isLibraryPlugin = false
            throw GradleException(PLUGIN_EXCEPTION)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getBootClasspath(): List<File> {
        return extAndroid.bootClasspath ?: plugin::class.java.getMethod("getRuntimeJarList").invoke(plugin) as List<File>
    }

    fun aspectj(): AspectJExtension {
        return project.extensions.getByType(AspectJExtension::class.java)
    }
}