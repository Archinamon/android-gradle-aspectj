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
import com.archinamon.utils.VariantUtils
import com.google.common.collect.Sets
import org.aspectj.util.FileUtil
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

import static com.archinamon.utils.StatusLogger.logAugmentationStart
import static com.archinamon.utils.StatusLogger.logAugmentationFinish
import static com.archinamon.utils.StatusLogger.logEnvInvalid
import static com.archinamon.utils.StatusLogger.logIgnoreInpathJars
import static com.archinamon.utils.StatusLogger.logJarAspectAdded
import static com.archinamon.utils.StatusLogger.logJarInpathAdded
import static com.archinamon.utils.StatusLogger.logNoAugmentation
import static com.archinamon.utils.StatusLogger.logWeaverBuildPolicy

class AspectJAppTransform extends Transform {

    def static final TRANSFORM_NAME        = "aspectj";
    def static final AJRUNTIME             = "aspectjrt";
    def static final SLICER_DETECTED_ERROR = "Running with InstantRun slicer when weaver extended not allowed!";

    Project project;
    BuildPolicy policy;
    AndroidConfig config;
    AspectJExtension extension;

    AspectJWeaver aspectJWeaver;
    AspectJMergeJars aspectJMerger;

    public AspectJAppTransform(Project project) {
        this.project = project;
        this.aspectJWeaver = new AspectJWeaver(project);
        this.aspectJMerger = new AspectJMergeJars(this);
    }

    def withPolicy(BuildPolicy policy) {
        this.policy = policy;
        return this;
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
            VariantUtils.getVariantDataList(config.plugin).each { setupVariant(aspectJWeaver, it); }

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

    def <T extends BaseVariantData<? extends BaseVariantOutputData>> void setupVariant(AspectJWeaver aspectJWeaver, T variantData) {
        if (variantData.getScope().getInstantRunBuildContext().isInInstantRunMode()) {
            if (modeComplex()) {
                throw new GradleException(SLICER_DETECTED_ERROR);
            }
        }

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
        return modeComplex() ?
            TransformManager.SCOPE_FULL_PROJECT : Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @Override
    Set<QualifiedContent.Scope> getReferencedScopes() {
        return modeComplex() ?
            super.getReferencedScopes() : TransformManager.SCOPE_FULL_PROJECT;
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
        includeCompiledAspects(transformInvocation, outputDir);
        Collection<TransformInput> inputs = modeComplex() ? transformInvocation.inputs : transformInvocation.referencedInputs;

        inputs.each { input ->
            if (input.directoryInputs.empty && input.jarInputs.empty)
                return; //if no inputs so nothing to proceed

            input.directoryInputs.each { DirectoryInput dir ->
                aspectJWeaver.inPath << dir.file;
                aspectJWeaver.classPath << dir.file;
            }
            input.jarInputs.each { JarInput jar ->
                aspectJWeaver.classPath << jar.file;

                if (modeComplex()) {
                    if (project.aspectj.includeAllJars || (!includeJars.empty && isIncludeFilterMatched(jar.file, includeJars))) {
                        logJarInpathAdded(jar);
                        aspectJWeaver.inPath << jar.file;
                    } else {
                        copyJar(outputProvider, jar);
                    }
                } else {
                    if (!includeJars.empty) logIgnoreInpathJars();
                }

                if (!includeAspects.empty && isIncludeFilterMatched(jar.file, includeAspects)) {
                    logJarAspectAdded(jar);
                    aspectJWeaver.aspectPath << jar.file;
                }
            }
        }

        def hasAjRt = aspectJWeaver.classPath.find { it.name.contains(AJRUNTIME); };

        if (hasAjRt) {
            logWeaverBuildPolicy(policy);
            aspectJWeaver.doWeave();

            if (modeComplex()) {
                aspectJMerger.doMerge(outputProvider, outputDir);
            }

            logAugmentationFinish();
        } else {
            logEnvInvalid();
            logNoAugmentation();
        }
    }

    def modeComplex() {
        policy == BuildPolicy.COMPLEX;
    }

    /* Internal */

    def private includeCompiledAspects(TransformInvocation transformInvocation, File outputDir) {
        def compiledAj = project.file("$project.buildDir/aspectj/${(transformInvocation.context as TransformTask).variantName}");
        if (compiledAj.exists()) {
            aspectJWeaver.aspectPath << compiledAj;

            //copy compiled .class files to output directory
            FileUtil.copyDir compiledAj, outputDir;
        }
    }

    def static copyJar(TransformOutputProvider outputProvider, JarInput jarInput) {
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

    def isExcludeFilterMatched(File file, List<String> filters) {
        isFilterMatched(file, filters, FilterPolicy.EXCLUDE);
    }

    def isIncludeFilterMatched(File file, List<String> filters) {
        isFilterMatched(file, filters, FilterPolicy.INCLUDE);
    }

    def isFilterMatched(File file, List<String> filters, FilterPolicy filterPolicy) {
        if (file == null) {
            return false;
        }

        if (filters == null || filters.isEmpty()) {
            return filterPolicy == FilterPolicy.INCLUDE;
        }

        String str = AarExploringHelper.findPackageNameIfAar(file);
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

    enum BuildPolicy {
        SIMPLE,
        COMPLEX,
        LIBRARY
    }
}
