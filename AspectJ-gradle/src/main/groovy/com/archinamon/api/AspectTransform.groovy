package com.archinamon.api

import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.utils.FileUtils
import com.archinamon.AndroidConfig
import com.archinamon.AspectJExtension
import com.archinamon.VariantUtils
import org.aspectj.util.FileUtil
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

class AspectTransform extends Transform {

    def static final TRANSFORM_NAME = "aspectj";

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
            aspectJWeaver.ignoreErrors = extension.ignoreErrors;
            aspectJWeaver.setLogFile(extension.logFileName);
        }
        this;
    }

    def <T extends BaseVariantData<? extends BaseVariantOutputData>> void setupVariant(AspectJWeaver aspectJWeaver, AndroidConfig config, T variantData) {
        def JavaCompile javaTask = VariantUtils.getJavaTask(variantData);
        VariantUtils.getAjSourceAndExcludeFromJavac(project, variantData);
        aspectJWeaver.encoding = javaTask.options.encoding;
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

    @Override //support of older gradle plugins
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        this.transform(new TransformInvocationBuilder(context)
                .addInputs(inputs)
                .addReferencedInputs(referencedInputs)
                .addOutputProvider(outputProvider)
                .setIncrementalMode(isIncremental).build());
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        TransformOutputProvider outputProvider = transformInvocation.outputProvider;
        List<String> includeJarFilter = project.aspectj.includeJarFilter;
        List<String> excludeJarFilter = project.aspectj.excludeJarFilter;

        if (!transformInvocation.incremental) {
            outputProvider.deleteAll();
        }

        final File outputDir = outputProvider.getContentLocation(TRANSFORM_NAME, outputTypes, scopes, Format.DIRECTORY);
        FileUtils.mkdirs(outputDir);
        FileUtils.emptyFolder(outputDir);

        aspectJWeaver.setAjSources(findAjSourcesForVariant(transformInvocation.context.variantName));
        aspectJWeaver.destinationDir = outputDir.absolutePath;
        aspectJWeaver.bootClasspath = config.bootClasspath.join(File.pathSeparator);
        aspectJWeaver.sourceCompatibility = JavaVersion.VERSION_1_7.toString();
        aspectJWeaver.targetCompatibility = JavaVersion.VERSION_1_7.toString();

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
                if (extension.defaultIncludeAllJars) {
                    if (excludeJarFilter.empty || !isExcludeFilterMatched(jarPath, excludeJarFilter)) {
                        includeJar(jarInput, jarPath);
                    } else {
                        excludeJar(outputProvider, jarInput, jarPath);
                    }
                } else {
                    if (!includeJarFilter.empty && isIncludeFilterMatched(jarPath, includeJarFilter)) {
                        includeJar(jarInput, jarPath);
                    } else {
                        excludeJar(outputProvider, jarInput, jarPath);
                    }
                }
            }
        }

        aspectJWeaver.doWeave();
        aspectJMerger.doMerge(outputProvider, outputDir);
    }

    def private void includeJar(JarInput jarInput, String jarPath) {
        println "includeJar :: ${jarPath}";
        aspectJWeaver.inPath << jarInput.file;
    }

    def static void excludeJar(TransformOutputProvider provider, JarInput jarInput, String jarPath) {
        println "excludeJar :: ${jarPath}";
        copyJar(provider, jarInput);
    }

    /* Internal */

    File[] findAjSourcesForVariant(String variantName) {
        def possibleDirs = [project.file("src/main/aspectj")];
        def String[] types = variantName.split("(?=\\p{Upper})");

        File[] root = project.file("src").listFiles();
        root.each { File file ->
            types.each {
                if (file.name.contains(it.toLowerCase()) &&
                        file.list().any { it.contains("aspectj"); } &&
                        !possibleDirs.contains(file)) {
                    possibleDirs << new File(file, 'aspectj');
                }
            }
        }

        possibleDirs.toArray(new File[possibleDirs.size()]);
    }

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