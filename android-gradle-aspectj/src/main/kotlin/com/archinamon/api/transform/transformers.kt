package com.archinamon.api.transform

import org.gradle.api.Project

/**
 * Simple transformers declarations and common data
 *
 * @author archinamon on 07.10.17.
 */

internal const val TRANSFORM_NAME = "aspectj"

enum class BuildPolicy {

    SIMPLE,
    COMPLEX,
    LIBRARY
}

internal class StandardTransformer(project: Project): AspectJTransform(project, BuildPolicy.SIMPLE)
internal class ExtendedTransformer(project: Project): AspectJTransform(project, BuildPolicy.COMPLEX)
internal class TestsTransformer(project: Project): AspectJTransform(project, BuildPolicy.COMPLEX)