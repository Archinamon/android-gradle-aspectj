package com.archinamon

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import com.android.build.gradle.internal.plugins.TestPlugin
import com.archinamon.plugin.ConfigScope
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

private const val ASPECTJ_PLUGIN = "com.archinamon.aspectj"
const val RETROLAMBDA = "me.tatarka.retrolambda"
const val MISDEFINITION = "Illegal definition: $ASPECTJ_PLUGIN should be defined after $RETROLAMBDA plugin"

private const val TAG = "AJC:"
private const val PLUGIN_EXCEPTION = "$TAG You must apply the Android plugin or the Android library plugin"

internal class AndroidConfig(val project: Project, val scope: ConfigScope) {

    val extAndroid: BaseExtension
    val isLibraryPlugin: Boolean
    val plugin: BasePlugin<*, *, *>

    init {
        when {
            project.plugins.hasPlugin(AppPlugin::class.java) -> {
                extAndroid = project.extensions.getByType(AppExtension::class.java)
                plugin = project.plugins.getPlugin(AppPlugin::class.java)
                isLibraryPlugin = false
            }

            project.plugins.hasPlugin(LibraryPlugin::class.java) -> {
                extAndroid = project.extensions.getByType(LibraryExtension::class.java)
                plugin = project.plugins.getPlugin(LibraryPlugin::class.java)
                isLibraryPlugin = true
            }

            project.plugins.hasPlugin(TestPlugin::class.java) -> {
                extAndroid = project.extensions.getByType(TestExtension::class.java)
                plugin = project.plugins.getPlugin(TestPlugin::class.java)
                isLibraryPlugin = false
            }

            else -> {
                isLibraryPlugin = false
                throw GradleException(PLUGIN_EXCEPTION)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getBootClasspath(): List<File> {
        return extAndroid.bootClasspath
    }

    fun aspectj(): AspectJExtension {
        return project.extensions.getByType(AspectJExtension::class.java)
    }
}