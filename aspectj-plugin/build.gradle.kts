import com.archinamon.gradle.DeployerExtension
import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Date

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply {
    plugin("com.archinamon.gradle.yandex-maven")
    plugin("com.jfrog.bintray")
}

group = "com.archinamon"
version = "3.2.2"

gradlePlugin {
    (plugins) {
        "com.archinamon.aspectj" {
            id = "com.archinamon.aspectj"
            implementationClass = "com.archinamon.plugin.AspectJWrapper\$Standard"
        }

        "com.archinamon.aspectj-ext" {
            id = "com.archinamon.aspectj-ext"
            implementationClass = "com.archinamon.plugin.AspectJWrapper\$Extended"
        }

        "com.archinamon.aspectj-provides" {
            id = "com.archinamon.aspectj-provides"
            implementationClass = "com.archinamon.plugin.AspectJWrapper\$Provides"
        }

        "com.archinamon.aspectj-test" {
            id = "com.archinamon.aspectj-test"
            implementationClass = "com.archinamon.plugin.AspectJWrapper\$Test"
        }
    }
}

tasks {
    val sourcesJar by creating(Jar::class) {
        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        classifier = "sources"
        from(java.sourceSets["main"].allSource)
    }

    val javadocJar by creating(Jar::class) {
        dependsOn(JavaPlugin.JAVADOC_TASK_NAME)
        classifier = "javadoc"
        from(java.docsDir)
    }

    artifacts {
        add("default", sourcesJar)
        add("default", javadocJar)
    }
}

val kotlinVersion: String by properties
val aspectjVersion: String by properties

dependencies {
    compile(kotlin("stdlib-jdk8", kotlinVersion))
    compileOnly(gradleApi())
    compileOnly("com.android.tools.build:gradle:3.0.1")
    compile("org.aspectj:aspectjrt:$aspectjVersion")
    compile("org.aspectj:aspectjtools:$aspectjVersion")

    testCompile(gradleTestKit())
    testCompile("junit:junit:4.12")
    testCompile("org.junit.jupiter:junit-jupiter-api:5.0.0-M3")
    testRuntime("org.junit.vintage:junit-vintage-engine:4.12.0-M1")
    testCompile(kotlin("test-junit", kotlinVersion))
}

//configure<DeployerExtension> {
//    localDeploy = true
//    localRepoPath = "$buildDir/m2"
//}

if (project.hasProperty("user") && project.hasProperty("apiKey")) {
    configure<BintrayExtension> {
        user = project.properties["user"].toString()
        key = project.properties["apiKey"].toString()
        setConfigurations("archives")
        pkg.apply {
            repo = "maven"
            name = "aspectj-plugin"
            vcsUrl = "https://github.com/Archinamon/GradleAspectJ-Android"
            setLicenses("Apache-2.0")
            publish = true
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

tasks.withType<Upload> {
    dependsOn(tasks.withType<Test>())
}