package com.archinamon

import groovy.util.logging.Slf4j
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Testing mock project
 *
 * @author archinamon on 11/04/17.
 */
@Slf4j
class AndroidPluginTest {

    private val aspectjVersion = AspectJExtension().ajc

    private val androidGradleVersion = "7.0.2"

    private val jarFile = File("build/libs/").listFiles { file ->
        "javadoc" !in file.name && "sources" !in file.name
    }.first().absolutePath

    private val rootTestDir = File("build/unitTests")
        get() {
            field.mkdirs()
            return field
        }

    @field:[Rule JvmField]
    var testProjectDir = TemporaryFolder(rootTestDir)

    private lateinit var buildFile: File

    @Before
    fun setup() {
        testProjectDir.newFile("settings.gradle")
        buildFile = testProjectDir.newFile("build.gradle")

        //local.properties with android-sdk path
        testProjectDir.newFile("./local.properties")
                .writeText("sdk.dir=${System.getenv("ANDROID_HOME")}")

        //AndroidManifest.xml
        File(rootTestDir.listFiles()!!.first(), "src/main").mkdirs()
        testProjectDir.newFile("./src/main/AndroidManifest.xml")
                .writeText("<manifest package=\"com.example.test\"/>")
    }

    @Test
    fun applyingAspectJPlugin() {
        buildFile.writeText("""
            buildscript {
                $REPOSITORIES

                dependencies {
                    // hack to avoid local pom-generation
                    classpath 'org.aspectj:aspectjrt:$aspectjVersion'
                    classpath 'org.aspectj:aspectjtools:$aspectjVersion'

                    // main dependencies
                    classpath 'com.android.tools.build:gradle:$androidGradleVersion'
                    classpath files('${jarFile}')
                }
            }

            $SIMPLE_PLUGIN_IMPLYING
        """.trimIndent())

        val result = GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.root)
                .withArguments("bundle",  "--info", "--stacktrace")
                .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":bundle")!!.outcome)
    }

    @Test
    fun applyingAspectJPluginWithIncludeJar() {
        buildFile.writeText("""
            buildscript {
                $REPOSITORIES

                dependencies {
                    // hack to avoid local pom-generation
                    classpath 'org.aspectj:aspectjrt:$aspectjVersion'
                    classpath 'org.aspectj:aspectjtools:$aspectjVersion'

                    // main dependencies
                    classpath 'com.android.tools.build:gradle:$androidGradleVersion'
                    classpath files('${jarFile}')
                }
            }

            $COMPLEX_PLUGIN_IMPLYING
        """.trimIndent())

        val result = GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.root)
                .withArguments("bundle",  "--info", "--stacktrace")
                .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":bundle")!!.outcome)
    }

    @Test
    fun applyingAspectJPluginCausingNoClassDef() {
        buildFile.writeText("""
            buildscript {
                $REPOSITORIES

                dependencies {
                    // hack to avoid local pom-generation
                    classpath 'org.aspectj:aspectjrt:$aspectjVersion'
                    classpath 'org.aspectj:aspectjtools:$aspectjVersion'

                    // main dependencies
                    classpath 'com.android.tools.build:gradle:$androidGradleVersion'
                    classpath files('${jarFile}')
                    classpath 'com.squareup.leakcanary:leakcanary-android:2.2'
                }
            }

            $SIMPLE_PLUGIN_IMPLYING
        """.trimIndent())

        val result = GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.root)
                .withArguments("bundle",  "--info", "--stacktrace")
                .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":bundle")!!.outcome)
    }

    @Test
    fun runningTestsWithAjAugmenting() {
        buildFile.writeText("""
            buildscript {
                $REPOSITORIES

                dependencies {
                    // hack to avoid local pom-generation
                    classpath 'org.aspectj:aspectjrt:$aspectjVersion'
                    classpath 'org.aspectj:aspectjtools:$aspectjVersion'

                    // main dependencies
                    classpath 'com.android.tools.build:gradle:$androidGradleVersion'
                    classpath files('${jarFile}')
                }
            }

            $SIMPLE_PLUGIN_IMPLYING
            $DEPENDENCIES_WITH_TESTS
        """.trimIndent())

        // simple unit test
        File(rootTestDir.listFiles()!!.first(), "src/test/java/com/example/test").mkdirs()
        testProjectDir.newFile("./src/test/java/com/example/test/SimpleTest.java")
                .writeText(SIMPLE_TEST_BODY_JAVA.trimIndent())

        // data provider to inject aspect in
        testProjectDir.newFile("./src/test/java/com/example/test/DataProvider.java")
                .writeText(SIMPLE_TEST_PROVIDER_BODY_JAVA.trimIndent())

        // simple test augmenting
        File(rootTestDir.listFiles()!!.first(), "src/test/aspectj/com/example/xpoint").mkdirs()
        testProjectDir.newFile("./src/test/aspectj/com/example/xpoint/TestMutator.aj")
                .writeText(SIMPLE_ASPECT_FOR_TEST_AUGMENTING.trimIndent())

        val result = GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.root)
                .withArguments("test", "--info", "--stacktrace")
                .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":test")!!.outcome)
    }
}
