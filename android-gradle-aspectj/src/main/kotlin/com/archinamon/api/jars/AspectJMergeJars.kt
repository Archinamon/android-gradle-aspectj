package com.archinamon.api.jars

import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.archinamon.api.transform.AspectJTransform
import java.io.File
import java.nio.file.Files

/**
 * Merging all jars and aars in project with dependenciesO
 * This runs when AspectJ augments jar's/aar's bytecode
 *
 * @author archinamon on 13/03/17.
 */
internal class AspectJMergeJars {

    private val target = com.archinamon.api.transform.TRANSFORM_NAME

    internal fun doMerge(transform: AspectJTransform, context: TransformInvocation, resultDir: File) {
        if (resultDir.listFiles()?.isNotEmpty() == true) {
            val jarFile = context.outputProvider.getContentLocation(target, transform.outputTypes, transform.scopes, Format.JAR)
            Files.createDirectory(jarFile.parentFile.toPath())
            jarFile.delete()

            val jarMerger = JarMerger(jarFile.toPath(), JarMerger.CLASSES_ONLY)
            try {
                jarMerger.addDirectory(resultDir.toPath())
            } catch (e: Exception) {
                throw TransformException(e)
            } finally {
                jarMerger.close()
            }
        }

        resultDir.delete()
    }
}