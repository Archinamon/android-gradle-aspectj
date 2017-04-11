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

internal const val TRANSFORM_NAME = "aspectj"
private const val AJRUNTIME = "aspectjrt"
private const val SLICER_DETECTED_ERROR = "Running with InstantRun slicer when weaver extended not allowed!"

enum class BuildPolicy {
    SIMPLE,
    COMPLEX,
    LIBRARY
}

internal class StdTransformer(project: Project): AspectJTransform(project, BuildPolicy.SIMPLE)
internal class ExtTransformer(project: Project): AspectJTransform(project, BuildPolicy.COMPLEX)
internal class LibTransformer(project: Project): AspectJTransform(project, BuildPolicy.LIBRARY) {

    override fun getScopes(): MutableSet<QualifiedContent.Scope> {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT)
    }

    override fun getReferencedScopes(): MutableSet<QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }
}

internal sealed class AspectJTransform(val project: Project, private val policy: BuildPolicy): Transform() {

    lateinit var config: AndroidConfig

    val aspectJWeaver: AspectJWeaver = AspectJWeaver(project)
    val aspectJMerger: AspectJMergeJars = AspectJMergeJars()

    fun withConfig(config: AndroidConfig): AspectJTransform {
        this.config = config
        return this
    }

    fun prepareProject(): AspectJTransform {
        project.afterEvaluate {
            getVariantDataList(config.plugin).forEach(this::setupVariant)

            aspectJWeaver.weaveInfo = config.aspectj().weaveInfo
            aspectJWeaver.debugInfo = config.aspectj().debugInfo
            aspectJWeaver.addSerialVUID = config.aspectj().addSerialVersionUID
            aspectJWeaver.noInlineAround = config.aspectj().noInlineAround
            aspectJWeaver.ignoreErrors = config.aspectj().ignoreErrors
            aspectJWeaver.transformLogFile = config.aspectj().transformLogFile
            aspectJWeaver.breakOnError = config.aspectj().breakOnError
            aspectJWeaver.experimental = config.aspectj().experimental
            aspectJWeaver.ajcArgs from config.aspectj().ajcArgs
        }

        return this
    }

    fun <T: BaseVariantData<out BaseVariantOutputData>> setupVariant(variantData: T) {
        if (variantData.scope.instantRunBuildContext.isInInstantRunMode) {
            if (modeComplex()) {
                throw GradleException(SLICER_DETECTED_ERROR)
            }
        }

        val javaTask = getJavaTask(variantData)
        getAjSourceAndExcludeFromJavac(project, variantData)
        aspectJWeaver.encoding = javaTask!!.options.encoding
        aspectJWeaver.sourceCompatibility = JavaVersion.VERSION_1_7.toString()
        aspectJWeaver.targetCompatibility = JavaVersion.VERSION_1_7.toString()
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
        val outputProvider = transformInvocation.outputProvider
        val includeJars = config.aspectj().includeJar
        val includeAspects = config.aspectj().includeAspectsFromJar

        if (!transformInvocation.isIncremental) {
            outputProvider.deleteAll()
        }

        val outputDir = outputProvider.getContentLocation(TRANSFORM_NAME, outputTypes, scopes, Format.DIRECTORY)
        if (outputDir.isDirectory) FileUtils.deleteDirectoryContents(outputDir)
        FileUtils.mkdirs(outputDir)

        aspectJWeaver.destinationDir = outputDir.absolutePath
        aspectJWeaver.bootClasspath = config.getBootClasspath().joinToString(separator = File.pathSeparator)

        // clear weaver input, so each transformation can have its own configuration
        // (e.g. different build types / variants)
        // thanks to @philippkumar
        aspectJWeaver.inPath.clear()
        aspectJWeaver.aspectPath.clear()

        logAugmentationStart()

        // attaching source classes compiled by compile${variantName}AspectJ task
        includeCompiledAspects(transformInvocation, outputDir)
        val inputs = if (modeComplex()) transformInvocation.inputs else transformInvocation.referencedInputs

        inputs.forEach proceedInputs@ { input ->
            if (input.directoryInputs.isEmpty() && input.jarInputs.isEmpty())
                return@proceedInputs //if no inputs so nothing to proceed

            input.directoryInputs.forEach { dir ->
                aspectJWeaver.inPath shl dir.file
                aspectJWeaver.classPath shl dir.file
            }
            input.jarInputs.forEach { jar ->
                aspectJWeaver.classPath shl jar.file

                if (modeComplex()) {
                    if (config.aspectj().includeAllJars || (includeJars.isNotEmpty() && isIncludeFilterMatched(jar.file, includeJars))) {
                        logJarInpathAdded(jar)
                        aspectJWeaver.inPath shl jar.file
                    } else {
                        copyJar(outputProvider, jar)
                    }
                } else {
                    if (includeJars.isNotEmpty()) logIgnoreInpathJars()
                }

                if (includeAspects.isNotEmpty() && isIncludeFilterMatched(jar.file, includeAspects)) {
                    logJarAspectAdded(jar)
                    aspectJWeaver.aspectPath shl jar.file
                }
            }
        }

        val hasAjRt = aspectJWeaver.classPath.any { it.name.contains(AJRUNTIME); }

        if (hasAjRt) {
            logWeaverBuildPolicy(policy)
            aspectJWeaver.doWeave()
            aspectJMerger.doMerge(this, transformInvocation, outputDir)

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

    private fun includeCompiledAspects(transformInvocation: TransformInvocation, outputDir: File) {
        val compiledAj = project.file("${project.buildDir}/aspectj/${(transformInvocation.context as TransformTask).variantName}")
        if (compiledAj.exists()) {
            aspectJWeaver.aspectPath shl compiledAj

            //copy compiled .class files to output directory
            FileUtil.copyDir(compiledAj, outputDir)
        }
    }

    private fun copyJar(outputProvider: TransformOutputProvider, jarInput: JarInput?): Boolean {
        if (jarInput === null) {
            return false
        }

        var jarName = jarInput.name
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length - 4)
        }

        val dest: File = outputProvider.getContentLocation(jarName, jarInput.contentTypes, jarInput.scopes, Format.JAR)

        FileUtil.copyFile(jarInput.file, dest)

        return true
    }
}
