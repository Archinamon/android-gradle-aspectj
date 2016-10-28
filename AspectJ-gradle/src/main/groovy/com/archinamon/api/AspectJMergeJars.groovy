package com.archinamon.api;

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.transforms.JarMerger
import com.android.builder.packaging.ZipAbortException
import com.android.builder.packaging.ZipEntryFilter
import com.android.utils.FileUtils
import groovy.transform.CompileStatic

/**
 * TODO: Add destription
 *
 * @author archinamon on 15/07/16.
 */
@Deprecated
public class AspectJMergeJars {

    private final String target = AspectTransform.TRANSFORM_NAME;
    private AspectTransform transform;

    AspectJMergeJars(AspectTransform transformer) {
        this.transform = transformer;
    }

    @CompileStatic
    void doMerge(TransformOutputProvider outputProvider, File resultDir) {
        if (resultDir.listFiles().length > 0) {
            File jarFile = outputProvider.getContentLocation(target, transform.getOutputTypes(), transform.getScopes(), Format.JAR);
            FileUtils.mkdirs(jarFile.getParentFile());
            FileUtils.deleteIfExists(jarFile);

            JarMerger jarMerger = new JarMerger(jarFile);
            try {
                jarMerger.setFilter(new ZipEntryFilter() {
                    @Override
                    boolean checkEntry(String archivePath) throws ZipAbortException {
                        return archivePath.endsWith(SdkConstants.DOT_CLASS);
                    }
                });

                jarMerger.addFolder(resultDir);
            } catch (Exception e) {
                throw new TransformException(e);
            } finally {
                jarMerger.close();
            }

        }

        FileUtils.deletePath(resultDir);
    }
}
