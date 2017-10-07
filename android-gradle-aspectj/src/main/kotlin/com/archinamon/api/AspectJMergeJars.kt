package com.archinamon.api

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.transforms.JarMerger
import com.android.utils.FileUtils
import com.archinamon.api.transform.AspectJTransform
import java.io.File

/**
 * Merging all jars and aars in project with dependencies
 * This runs when AspectJ augments jar's/aar's bytecode
 *
 * @author archinamon on 13/03/17.
 */
internal class AspectJMergeJars {

    private val target = com.archinamon.api.transform.TRANSFORM_NAME

    internal fun doMerge(transform: AspectJTransform, context: TransformInvocation, resultDir: File) {
        if (resultDir.listFiles().isNotEmpty()) {
            val jarFile = context.outputProvider.getContentLocation(target, transform.outputTypes, transform.scopes, Format.JAR)
            FileUtils.mkdirs(jarFile.parentFile)
            FileUtils.deleteIfExists(jarFile)

            val jarMerger = JarMerger(jarFile)
            try {
                jarMerger.setFilter { archivePath -> archivePath.endsWith(SdkConstants.DOT_CLASS) }
                jarMerger.addFolder(resultDir)
            } catch (e: Exception) {
                throw TransformException(e)
            } finally {
                jarMerger.close()
            }
        }

        FileUtils.deletePath(resultDir)
    }
}