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
import com.google.common.collect.Sets
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

import static com.archinamon.StatusLogger.logAugmentationStart
import static com.archinamon.StatusLogger.logAugmentationFinish
import static com.archinamon.StatusLogger.logEnvInvalid
import static com.archinamon.StatusLogger.logJarAspectAdded
import static com.archinamon.StatusLogger.logJarInpathAdded
import static com.archinamon.StatusLogger.logNoAugmentation

class AspectTransform extends Transform {

    def static final TRANSFORM_NAME = "aspectj";
    def static final AJRUNTIME      = "aspectjrt";

    Project project;
    AndroidConfig config;
    AspectJExtension extension;

    AspectJWeaver aspectJWeaver;

    public AspectTransform(Project project) {
        this.project = project;
        this.aspectJWeaver = new AspectJWeaver(project);
    }

    def withConfig(AndroidConfig config) {
        this.config = config;
        return this;
    }

    def withExtension(AspectJExtension extension) {
        this.extension = extension;
        return this;
    }

    def prepareProject() {
        project.afterEvaluate {
            VariantUtils.getVariantDataList(config.plugin).each { setupVariant(aspectJWeaver, config, it); }

            aspectJWeaver.weaveInfo = extension.weaveInfo;
            aspectJWeaver.debugInfo = extension.debugInfo;
            aspectJWeaver.addSerialVUID = extension.addSerialVersionUID;
            aspectJWeaver.noInlineAround = extension.noInlineAround;
            aspectJWeaver.ignoreErrors = extension.ignoreErrors;
            aspectJWeaver.setTransformLogFile(extension.transformLogFile);
            aspectJWeaver.breakOnError = extension.breakOnError;
            aspectJWeaver.experimental = extension.experimental;
            aspectJWeaver.ajcArgs.addAll extension.ajcArgs;
        }

        return this;
    }

    def <T extends BaseVariantData<? extends BaseVariantOutputData>> void setupVariant(AspectJWeaver aspectJWeaver, AndroidConfig config, T variantData) {
        def JavaCompile javaTask = VariantUtils.getJavaTask(variantData);
        VariantUtils.getAjSourceAndExcludeFromJavac(project, variantData);
        aspectJWeaver.encoding = javaTask.options.encoding;
        aspectJWeaver.sourceCompatibility = JavaVersion.VERSION_1_7.toString();
        aspectJWeaver.targetCompatibility = JavaVersion.VERSION_1_7.toString();
    }

    /* External API */

    @Override
    String getName() {
        return TRANSFORM_NAME;
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return Sets.immutableEnumSet(QualifiedContent.DefaultContentType.CLASSES);
    }

    @Override
    Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @Override
    Set<QualifiedContent.Scope> getReferencedScopes() {
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
        List<String> includeJars = extension.includeJar;
        List<String> includeAspects = extension.includeAspectsFromJar;

        if (!transformInvocation.incremental) {
            outputProvider.deleteAll();
        }

        final File outputDir = outputProvider.getContentLocation(TRANSFORM_NAME, outputTypes, scopes, Format.DIRECTORY);
        if (outputDir.isDirectory()) FileUtils.deleteDirectoryContents(outputDir);
        FileUtils.mkdirs(outputDir);

        aspectJWeaver.destinationDir = outputDir.absolutePath;
        aspectJWeaver.bootClasspath = config.bootClasspath.join(File.pathSeparator);

        // clear weaver input, so each transformation can have its own configuration
        // (e.g. different build types / variants)
        // thanks to @philippkumar
        aspectJWeaver.inPath.clear();
        aspectJWeaver.aspectPath.clear();

        logAugmentationStart();

        // attaching source classes compiled by compile${variantName}AspectJ task
        includeCompiledAspects(transformInvocation);

        transformInvocation.referencedInputs.each { input ->
            if (input.directoryInputs.empty && input.jarInputs.empty)
                return; //if no inputs so nothing to proceed

            input.directoryInputs.each { DirectoryInput dir ->
                aspectJWeaver.inPath << dir.file;
                aspectJWeaver.classPath << dir.file;
            }
            input.jarInputs.each { JarInput jar ->
                aspectJWeaver.classPath << jar.file;

                if (project.aspectj.includeAllJars || (!includeJars.empty && isIncludeFilterMatched(jar.file.absolutePath, includeJars))) {
                    logJarInpathAdded(jar);
                    aspectJWeaver.inPath << jar.file;
                }

                if (!includeAspects.empty && isIncludeFilterMatched(jar.file.absolutePath, includeAspects)) {
                    logJarAspectAdded(jar);
                    aspectJWeaver.aspectPath << jar.file;
                }
            }
        }

        def hasAjRt = aspectJWeaver.classPath.find { it.name.contains(AJRUNTIME); };

        if (hasAjRt) {
            aspectJWeaver.doWeave();
            logAugmentationFinish();
        } else {
            logEnvInvalid();
            logNoAugmentation();
        }
    }

    /* Internal */

    def private includeCompiledAspects(TransformInvocation transformInvocation) {
        def compiledAj = project.file("$project.buildDir/aspectj/${(transformInvocation.context as TransformTask).variantName}");
        if (compiledAj.exists()) {
            aspectJWeaver.aspectPath << compiledAj;
        }
    }

    def isExcludeFilterMatched(String str, List<String> filters) {
        isFilterMatched(str, filters, FilterPolicy.EXCLUDE);
    }

    def isIncludeFilterMatched(String str, List<String> filters) {
        isFilterMatched(str, filters, FilterPolicy.INCLUDE);
    }

    def isFilterMatched(String str, List<String> filters, FilterPolicy filterPolicy) {
        if (str == null) {
            return false;
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

    def static isContained(String str, String filter) {
        if (str == null) {
            return false;
        }

        String filterTmp = filter;
        if (str.contains(filterTmp)) {
            return true;
        } else {
            if (filterTmp.contains("/")) {
                return str.contains(filterTmp.replace("/", File.separator));
            } else if (filterTmp.contains("\\")) {
                return str.contains(filterTmp.replace("\\", File.separator));
            }
        }

        return false;
    }

    enum FilterPolicy {
        INCLUDE,
        EXCLUDE
    }
}
