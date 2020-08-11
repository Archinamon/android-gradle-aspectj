package com.archinamon.api.transform

import com.android.build.api.transform.Context
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.archinamon.utils.logBypassTransformation
import org.gradle.api.Project

internal class ProvidesTransformer(project: Project): AspectJTransform(project, BuildPolicy.SIMPLE) {

    override fun prepareProject(): AspectJTransform {
        return this
    }

    override fun transform(
            context: Context,
            inputs: Collection<TransformInput>,
            referencedInputs: Collection<TransformInput>,
            outputProvider: TransformOutputProvider,
            isIncremental: Boolean
    ) {
        logBypassTransformation()
    }

    override fun transform(transformInvocation: TransformInvocation) {
        logBypassTransformation()
    }
}