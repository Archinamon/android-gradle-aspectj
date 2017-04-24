package com.archinamon.api

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.transforms.JarMerger
import com.android.utils.FileUtils
import java.io.File

/**
 * TODO: Add description
 *
 * @author archinamon on 13/03/17.
 */
internal class AspectJMergeJars {

    private val target = TRANSFORM_NAME

    internal fun doMerge(transform: AspectJTransform, context: TransformInvocation, resultDir: File) {
        if (resultDir.listFiles().isNotEmpty()) {
            val jarFile = context.outputProvider.getContentLocation(target, transform.outputTypes, transform.scopes, Format.JAR)
            FileUtils.mkdirs(jarFile.parentFile)
            FileUtils.deleteIfExists(jarFile)

            val jarMerger: JarMerger = JarMerger(jarFile)
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