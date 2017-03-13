package com.archinamon.api

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.google.common.collect.Sets
import org.gradle.api.Project

class AspectJLibTransform extends AspectJAppTransform {

    def static final TRANSFORM_NAME = "aspectj";

    public AspectJLibTransform(Project project) {
        super(project);
    }

    /* External API */

    @Override
    String getName() {
        return TRANSFORM_NAME;
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @Override
    Set<QualifiedContent.Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }
}
