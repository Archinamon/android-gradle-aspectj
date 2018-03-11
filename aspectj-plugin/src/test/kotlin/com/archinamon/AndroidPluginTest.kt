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
                repositories {
                    google()
                    jcenter()
                    mavenCentral()
                }

                dependencies {
                    // hack to avoid pom-generation
                    classpath 'org.aspectj:aspectjrt:$aspectjVersion'
                    classpath 'org.aspectj:aspectjtools:$aspectjVersion'

                    // main dependencies
                    classpath 'com.android.tools.build:gradle:3.0.1'
                    classpath files('${jarFile.absolutePath}')
                }
            }

            apply plugin: 'com.android.application'
            apply plugin: 'com.archinamon.aspectj'

            android {
                compileSdkVersion 27

                defaultConfig {
                    applicationId 'com.example.test'
                    minSdkVersion 21
                    targetSdkVersion 27
                    versionCode 1
                    versionName "1.0"
                }
            }

            repositories {
                jcenter()
                mavenCentral()
            }
        """.trimIndent())

        val result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("build",  "--info", "--stacktrace")
                .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":build")!!.outcome)
    }
}