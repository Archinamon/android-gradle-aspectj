import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayUploadTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Date

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
}

apply {
    plugin("com.jfrog.bintray")
}

group = "com.archinamon"
version = "3.3.8"

gradlePlugin {
    (plugins) {
        register("com.archinamon.aspectj") {
            id = "com.archinamon.aspectj"
            implementationClass = "com.archinamon.plugin.AspectJWrapper\$Standard"
        }

        register("com.archinamon.aspectj-ext") {
            id = "com.archinamon.aspectj-ext"
            implementationClass = "com.archinamon.plugin.AspectJWrapper\$Extended"
        }

        register("com.archinamon.aspectj-provides") {
            id = "com.archinamon.aspectj-provides"
            implementationClass = "com.archinamon.plugin.AspectJWrapper\$Provides"
        }

        register("com.archinamon.aspectj-junit") {
            id = "com.archinamon.aspectj-junit"
            implementationClass = "com.archinamon.plugin.AspectJWrapper\$Test"
        }
    }
}

tasks {
    val sourcesJar by creating(Jar::class) {
        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        classifier = "sources"
        from(sourceSets["main"].allSource)
    }

    val javadocJar by creating(Jar::class) {
        dependsOn(JavaPlugin.JAVADOC_TASK_NAME)
        classifier = "javadoc"
        from("$buildDir/docs")
    }

    val pomFileDestPath = File("$buildDir/libs/${project.name}-${project.version}.pom")
    withType<GenerateMavenPom> {
        destination = pomFileDestPath
    }

    artifacts {
        add("archives", sourcesJar)
        add("archives", javadocJar)
        add("archives", pomFileDestPath)
    }
}

val androidGradleVersion: String by extra
val kotlinVersion: String by extra
val aspectjVersion: String by extra

dependencies {
    compileOnly(kotlin("stdlib-jdk8", kotlinVersion))
    compileOnly(gradleApi())
    compileOnly("com.android.tools.build:gradle:$androidGradleVersion")
    compile("org.aspectj:aspectjrt:$aspectjVersion")
    compile("org.aspectj:aspectjtools:$aspectjVersion")

    testCompile(gradleTestKit())
    testCompile("junit:junit:4.12")
    testCompile("org.junit.jupiter:junit-jupiter-api:5.0.0-M3")
    testRuntime("org.junit.vintage:junit-vintage-engine:4.12.0-M1")
    testCompile(kotlin("test-junit", kotlinVersion))
}

if (project.hasProperty("user") && project.hasProperty("apiKey")) {
    configure<BintrayExtension> {
        user = project.properties["user"].toString()
        key = project.properties["apiKey"].toString()

        publish = true

        setConfigurations("archives")
        pkg.apply {
            repo = "maven"
            name = "android-gradle-aspectj"
            vcsUrl = "https://github.com/Archinamon/GradleAspectJ-Android"
            setLicenses("Apache-2.0")
            version.apply {
                name = project.version.toString()
                released = Date().toString()
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    dependsOn(tasks.withType<Jar>())
}

tasks.withType<GenerateMavenPom> {
    dependsOn(tasks.withType<Jar>())
}

tasks.withType<BintrayUploadTask> {
    dependsOn(tasks.withType<GenerateMavenPom>())
    dependsOn(tasks.withType<Test>())
}