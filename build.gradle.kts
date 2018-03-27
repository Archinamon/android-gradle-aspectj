buildscript {
    repositories {
        jcenter()
        mavenCentral()
        maven {
            name = "Yandex"
            url = uri("http://artifactory.yandex.net/artifactory/public/")
        }
    }

    val kotlinVersion: String by properties
    dependencies {
        classpath(kotlin("gradle-plugin", kotlinVersion))
        classpath("com.archinamon.gradle:yandex-maven:1.1")
        classpath("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.7.3")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
        maven { url = uri("http://repository.jetbrains.com/utils") }
        maven { url = uri("https://dl.bintray.com/archinamon/maven") }
    }
}