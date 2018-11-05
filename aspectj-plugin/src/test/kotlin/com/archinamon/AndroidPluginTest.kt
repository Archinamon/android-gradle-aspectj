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
        buildFile = testProjectDir.newFile("build.gradle")

        //local.properties with android-sdk path
        testProjectDir.newFile("./local.properties").writeText("""
            sdk.dir=${System.getenv("ANDROID_HOME")}
        """.trimIndent())

        //AndroidManifest.xml
        File(rootTestDir.listFiles().first(), "src/main").mkdirs()
        testProjectDir.newFile("./src/main/AndroidManifest.xml").writeText("""
            <manifest package="com.example.test"/>
        """.trimIndent())
    }

    @Test
    fun applyingAspectJPlugin() {
        val aspectjVersion = AspectJExtension().ajc
        val jarFile = File("build/libs/").listFiles().first()

        buildFile.writeText("""
            buildscript {
                $REPOSITORIES

                dependencies {
                    // hack to avoid local pom-generation
                    classpath 'org.aspectj:aspectjrt:$aspectjVersion'
                    classpath 'org.aspectj:aspectjtools:$aspectjVersion'

                    // main dependencies
                    classpath 'com.android.tools.build:gradle:3.0.1'
                    classpath files('${jarFile.absolutePath}')
                }
            }

            $SIMPLE_PLUGIN_IMPLYING
        """.trimIndent())

        val result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("build",  "--info", "--stacktrace")
                .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":build")!!.outcome)
    }

    @Test
    fun runningTestsWithAjAugmenting() {
        val aspectjVersion = AspectJExtension().ajc
        val jarFile = File("build/libs/").listFiles().first()

        buildFile.writeText("""
            buildscript {
                $REPOSITORIES

                dependencies {
                    // hack to avoid local pom-generation
                    classpath 'org.aspectj:aspectjrt:$aspectjVersion'
                    classpath 'org.aspectj:aspectjtools:$aspectjVersion'

                    // main dependencies
                    classpath 'com.android.tools.build:gradle:3.0.1'
                    classpath files('${jarFile.absolutePath}')
                }
            }

            $SIMPLE_PLUGIN_IMPLYING
            $DEPENDENCIES_WITH_TESTS
        """.trimIndent())

        // simple unit test
        File(rootTestDir.listFiles().first(), "src/main/test/java/com/example/test").mkdirs()
        testProjectDir.newFile("./src/main/test/java/com/example/test/SimpleTest.java")
                .writeText(SIMPLE_TEST_BODY_JAVA.trimIndent())

        // simple test augmenting
        File(rootTestDir.listFiles().first(), "src/main/aspectj/java/com/example/xpoint").mkdirs()
        testProjectDir.newFile("./src/main/aspectj/java/com/example/xpoint/TestMutator.java")
                .writeText(SIMPLE_ASPECT_FOR_TEST_AUGMENTING.trimIndent())

        val result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("test",  "--info", "--stacktrace")
                .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":test")!!.outcome)
    }
}