package com.archinamon.api.transform

import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.internal.pipeline.TransformManager
import com.google.common.collect.Sets
import org.gradle.api.Project

internal class LibraryTransformer(project: Project): AspectJTransform(project, BuildPolicy.LIBRARY) {

    override fun getScopes(): MutableSet<QualifiedContent.Scope> {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT)
    }

    override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }
}
