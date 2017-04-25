package com.archinamon.api

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.utils.FileUtils
import com.archinamon.AndroidConfig
import com.archinamon.utils.*
import com.archinamon.utils.DependencyFilter.isIncludeFilterMatched
import com.google.common.collect.Sets
import org.aspectj.util.FileUtil
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import java.io.File
import java.util.*

internal const val TRANSFORM_NAME = "aspectj"
private const val AJRUNTIME = "aspectjrt"
private const val SLICER_DETECTED_ERROR = "Running with InstantRun slicer when weaver extended not allowed!"

enum class BuildPolicy {

    SIMPLE,
    COMPLEX,
    LIBRARY
}

internal class StdTransformer(config: AndroidConfig) : AspectJTransform(config, BuildPolicy.SIMPLE)
internal class ExtTransformer(config: AndroidConfig) : AspectJTransform(config, BuildPolicy.COMPLEX)
internal class TestTransformer(config: AndroidConfig) : AspectJTransform(config, BuildPolicy.COMPLEX) {

    override fun transform(transformInvocation: TransformInvocation) {
        // bypassing transformer for non-test variant data in ConfigScope.TEST
        if (bypass(transformInvocation.context)) {
            logBypassTransformation()

            if (!transformInvocation.isIncremental) {
                transformInvocation.outputProvider.deleteAll()
            }

            val outputDir = transformInvocation.outputProvider.getContentLocation(TRANSFORM_NAME, outputTypes, scopes, Format.DIRECTORY)
            transformInvocation.inputs.forEach {
                it.jarInputs.forEach { copyJar(transformInvocation.outputProvider, it) }
                it.directoryInputs.forEach { it.file.copyTo(outputDir) }
            }
            AspectJMergeJars().doMerge(this, transformInvocation, outputDir)
            return
        }
        super.transform(transformInvocation)
    }

    private fun bypass(ctx: Context): Boolean {
        val variant = (ctx as TransformTask).variantName
        return !variant.contains("androidtest", true)
    }

    override fun prepareProject(): AspectJTransform {
        config.project.afterEvaluate {
            getVariantDataList(config.plugin)
                    .filter { it.type.isForTesting }
                    .forEach {
                        val javaTask = getJavaTask(it)
                        encoding = javaTask!!.options.encoding
                        classpath from javaTask.classpath.files
                        sourceCompatibility = JavaVersion.VERSION_1_7.toString()
                        targetCompatibility = JavaVersion.VERSION_1_7.toString()
                    }
        }
        return this
    }
}

internal class LibTransformer(config: AndroidConfig) : AspectJTransform(config, BuildPolicy.LIBRARY) {

    override fun getScopes(): MutableSet<QualifiedContent.Scope> {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT)
    }

    override fun getReferencedScopes(): MutableSet<QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }
}

internal sealed class AspectJTransform(val config: AndroidConfig, private val policy: BuildPolicy) : Transform() {

    open fun prepareProject(): AspectJTransform {
        config.extAndroid.registerTransform(this)
        config.project.afterEvaluate {
            getVariantDataList(config.plugin).forEach {
                setupVariant(it, config.project)
            }
        }

        return this
    }

    lateinit var encoding: String
    lateinit var sourceCompatibility: String
    lateinit var targetCompatibility: String
    val classpath: MutableSet<File> = LinkedHashSet()

    fun <T : BaseVariantData<out BaseVariantOutputData>> setupVariant(variantData: T, project: Project) {
        if (variantData.scope.instantRunBuildContext.isInInstantRunMode) {
            if (modeComplex()) {
                throw GradleException(SLICER_DETECTED_ERROR)
            }
        }

        val javaTask = getJavaTask(variantData)
        getAjSourceAndExcludeFromJavac(project, variantData)
        encoding = javaTask!!.options.encoding

        classpath from javaTask.classpath.files
        sourceCompatibility = JavaVersion.VERSION_1_7.toString()
        targetCompatibility = JavaVersion.VERSION_1_7.toString()
    }

    /* External API */

    override fun getName(): String {
        return TRANSFORM_NAME
    }

    override fun getInputTypes(): Set<QualifiedContent.ContentType> {
        return Sets.immutableEnumSet(QualifiedContent.DefaultContentType.CLASSES)
    }

    override fun getOutputTypes(): Set<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return if (modeComplex()) TransformManager.SCOPE_FULL_PROJECT else Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT)
    }

    override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
        return if (modeComplex()) super.getReferencedScopes() else TransformManager.SCOPE_FULL_PROJECT
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun transform(context: Context, inputs: Collection<TransformInput>, referencedInputs: Collection<TransformInput>, outputProvider: TransformOutputProvider, isIncremental: Boolean) {
        this.transform(TransformInvocationBuilder(context)
                .addInputs(inputs)
                .addReferencedInputs(referencedInputs)
                .addOutputProvider(outputProvider)
                .setIncrementalMode(isIncremental).build())
    }

    override fun transform(transformInvocation: TransformInvocation) {
        if (!transformInvocation.isIncremental) {
            transformInvocation.outputProvider.deleteAll()
        }

        val outputDir = transformInvocation.outputProvider.getContentLocation(TRANSFORM_NAME, outputTypes, scopes, Format.DIRECTORY)
        if (outputDir.isDirectory) FileUtils.deleteDirectoryContents(outputDir)
        FileUtils.mkdirs(outputDir)

        val includeJars = config.aspectj().includeJar
        val includeAspects = config.aspectj().includeAspectsFromJar

        val aspectJWeaver: AspectJWeaver = AspectJWeaver(config.project).apply {
            inPath.clear()
            aspectPath.clear()
            classPath = this@AspectJTransform.classpath
            destinationDir = outputDir.absolutePath
            bootClasspath = config.getBootClasspath().joinToString(separator = File.pathSeparator)
            weaveInfo = config.aspectj().weaveInfo
            debugInfo = config.aspectj().debugInfo
            addSerialVUID = config.aspectj().addSerialVersionUID
            noInlineAround = config.aspectj().noInlineAround
            ignoreErrors = config.aspectj().ignoreErrors
            transformLogFile = config.aspectj().transformLogFile
            breakOnError = config.aspectj().breakOnError
            experimental = config.aspectj().experimental
            ajcArgs from config.aspectj().ajcArgs
            encoding = this@AspectJTransform.encoding
            sourceCompatibility = this@AspectJTransform.sourceCompatibility
            targetCompatibility = this@AspectJTransform.targetCompatibility
        }
        logAugmentationStart()

        // attaching source classes compiled by compile${variantName}AspectJ task
        includeCompiledAspects(transformInvocation, aspectJWeaver, outputDir)
        val inputs = if (modeComplex()) transformInvocation.inputs else transformInvocation.referencedInputs

        inputs.forEach proceedInputs@ { input ->
            if (input.directoryInputs.isEmpty() && input.jarInputs.isEmpty())
                return@proceedInputs //if no inputs so nothing to proceed

            input.directoryInputs.map { it.file }.let {
                aspectJWeaver.classPath from it
                aspectJWeaver.inPath from it
            }

            input.jarInputs.forEach { jar ->
                aspectJWeaver.classPath shl jar.file

                if (modeComplex()) {
                    if (config.aspectj().includeAllJars || isIncludeFilterMatched(jar.file, includeJars)) {
                        logJarInpathAdded(jar.file)
                        aspectJWeaver.inPath shl jar.file
                    } else {
                        copyJar(transformInvocation.outputProvider, jar)
                    }
                } else {
                    if (includeJars.isNotEmpty()) logIgnoreInpathJars()
                }

                if (isIncludeFilterMatched(jar.file, includeAspects)) {
                    logJarAspectAdded(jar.file)
                    aspectJWeaver.aspectPath shl jar.file
                }
            }
        }

        val hasAjRt = aspectJWeaver.classPath.any { it.name.contains(AJRUNTIME); }

        if (hasAjRt) {
            logWeaverBuildPolicy(policy)
            aspectJWeaver.doWeave()

            if (modeComplex()) {
                AspectJMergeJars().doMerge(this, transformInvocation, outputDir)
            }

            logAugmentationFinish()
        } else {
            logEnvInvalid()
            logNoAugmentation()
        }
    }

    fun modeComplex(): Boolean {
        return policy == BuildPolicy.COMPLEX
    }

    /* Internal */
    private fun includeCompiledAspects(transformInvocation: TransformInvocation, aspectJWeaver: AspectJWeaver, outputDir: File) {
        val compiledAj = config.project.file("${config.project.buildDir}/aspectj/${(transformInvocation.context as TransformTask).variantName}")
        if (compiledAj.exists()) {
            aspectJWeaver.aspectPath shl compiledAj

            //copy compiled .class files to output directory
            FileUtil.copyDir(compiledAj, outputDir)
        }
    }

    internal fun copyJar(outputProvider: TransformOutputProvider, jarInput: JarInput?): Boolean {
        if (jarInput === null) {
            return false
        }

        val dest: File = outputProvider.getContentLocation(
                jarInput.file.nameWithoutExtension,
                jarInput.contentTypes,
                jarInput.scopes,
                Format.JAR)

        FileUtil.copyFile(jarInput.file, dest)

        return true
    }
}
