package com.archinamon.api;

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.utils.FileUtils
import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.VariantUtils
import com.google.common.collect.Sets
import org.aspectj.util.FileUtil
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

class AspectTransform extends Transform {

    def private static final TRANSFORM_NAME = "aspectj";

    Project project;
    AndroidConfig config;
    AspectJExtension extension;

    AspectJWeaver aspectJWeaver;
    AspectJMergeJars aspectJMerger;

    public AspectTransform(Project project) {
        this.project = project;
        this.aspectJWeaver = new AspectJWeaver(project);
        this.aspectJMerger = new AspectJMergeJars(this);
    }

    AspectTransform withConfig(AndroidConfig config) {
        this.config = config;
        this;
    }

    AspectTransform withExtension(AspectJExtension extension) {
        this.extension = extension;
        this;
    }

    AspectTransform prepareProject() {
        project.afterEvaluate {
            VariantUtils.getVariantDataList(config.plugin).each { setupVariant(aspectJWeaver, config, it); }

            aspectJWeaver.weaveInfo = extension.weaveInfo;
            aspectJWeaver.addSerialVUID = extension.addSerialVersionUID;
            aspectJWeaver.noInlineAround = extension.noInlineAround;
            aspectJWeaver.setLogFile(extension.logFileName);
        }
        this;
    }

    def <T extends BaseVariantData<? extends BaseVariantOutputData>> void setupVariant(AspectJWeaver aspectJWeaver, AndroidConfig config, T variantData) {
        def JavaCompile javaTask = VariantUtils.getJavaTask(variantData);

        def aspects = VariantUtils.getAjSourceAndExcludeFromJavac(project, variantData);

        aspectJWeaver.setAjSources(aspects.files.toArray(new File[aspects.size()]));
        aspectJWeaver.encoding = javaTask.options.encoding;
        aspectJWeaver.bootClasspath = config.bootClasspath.join(File.pathSeparator);
        aspectJWeaver.sourceCompatibility = extension.javaVersion.toString();
        aspectJWeaver.targetCompatibility = extension.javaVersion.toString();
    }

    /* External API */

    @Override
    String getName() {
        return TRANSFORM_NAME;
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    boolean isIncremental() {
        return false;
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        TransformOutputProvider outputProvider = transformInvocation.outputProvider;
        List<String> includeJarFilter = project.aspectj.includeJarFilter;
        List<String> excludeJarFilter = project.aspectj.excludeJarFilter;

        if (!transformInvocation.incremental) {
            outputProvider.deleteAll();
        }

        final File resultDir = outputProvider.getContentLocation("aspectj", outputTypes, scopes, Format.DIRECTORY);
        FileUtils.mkdirs(resultDir);
        FileUtils.emptyFolder(resultDir);

        aspectJWeaver.destinationDir = resultDir.absolutePath;

        transformInvocation.inputs.each { input ->
            if (input.directoryInputs.empty && input.jarInputs.empty)
                return; //if no inputs so nothing to proceed

            for (DirectoryInput dirInput : input.directoryInputs) {
                aspectJWeaver.aspectPath << dirInput.file;
                aspectJWeaver.inPath << dirInput.file;
                aspectJWeaver.classPath << dirInput.file;
            }

            for (JarInput jarInput : input.jarInputs) {
                aspectJWeaver.aspectPath << jarInput.file;
                aspectJWeaver.classPath << jarInput.file;

                String jarPath = jarInput.file.absolutePath;
                if (isIncludeFilterMatched(jarPath, includeJarFilter) &&
                        !isExcludeFilterMatched(jarPath, excludeJarFilter)) {
                    println "includeJar :: ${jarPath}";
                    aspectJWeaver.inPath << jarInput.file;
                } else {
                    println "excludeJar :: ${jarPath}";
                    copyJar(outputProvider, jarInput);
                }
            }
        }

        aspectJWeaver.weave();
        aspectJMerger.doMerge(outputProvider, resultDir);
    }

    /* Internal */

    boolean isExcludeFilterMatched(String str, List<String> filters) {
        return isFilterMatched(str, filters, FilterPolicy.EXCLUDE);
    }

    boolean  isIncludeFilterMatched(String str, List<String> filters) {
        return isFilterMatched(str, filters, FilterPolicy.INCLUDE);
    }

    boolean isFilterMatched(String str, List<String> filters, FilterPolicy filterPolicy) {
        if(str == null) {
            return false
        }

        if (filters == null || filters.isEmpty()) {
            return filterPolicy == FilterPolicy.INCLUDE;
        }

        for (String s : filters) {
            if (isContained(str, s)) {
                return true;
            }
        }

        return false;
    }

    static boolean copyJar(TransformOutputProvider outputProvider, JarInput jarInput) {
        if (outputProvider == null || jarInput == null) {
            return false;
        }

        String jarName = jarInput.name;
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length() - 4);
        }

        File dest = outputProvider.getContentLocation(jarName, jarInput.contentTypes, jarInput.scopes, Format.JAR);

        FileUtil.copyFile(jarInput.file, dest);

        return true;
    }

    static boolean isContained(String str, String filter) {
        if (str == null) {
            return false;
        }

        String filterTmp = filter;
        if (str.contains(filterTmp)) {
            return true
        } else {
            if (filterTmp.contains("/")) {
                return str.contains(filterTmp.replace("/", File.separator));
            } else if (filterTmp.contains("\\")) {
                return str.contains(filterTmp.replace("\\", File.separator));
            }
        }

        return false
    }

    enum FilterPolicy {

        INCLUDE,
        EXCLUDE
    }
}