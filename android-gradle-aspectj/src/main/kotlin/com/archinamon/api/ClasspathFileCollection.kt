package com.archinamon.api

import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.TaskDependency
import java.io.File

class ClasspathFileCollection(
        private val files: Set<File>
) : AbstractFileCollection() {

    override fun getFiles() = files.toMutableSet()

    override fun getDisplayName() = DISPLAY_NAME

    private companion object {
        const val DISPLAY_NAME = "aspectjClasspath"
    }
}