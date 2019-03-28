package com.archinamon.api.jars

import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.utils.FileUtils
import com.archinamon.api.transform.AspectJTransform
import java.io.File

/**
 * Merging all jars and aars in project with dependenciesO
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

            val jarMerger = JarMerger(jarFile.toPath(), JarMerger.CLASSES_ONLY)
            try {
                jarMerger.addDirectory(resultDir.toPath())
            } catch (e: Exception) {
                throw TransformException(e)
            } finally {
                jarMerger.close()
            }
        }

        FileUtils.deletePath(resultDir)
    }
}