buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }

    val kotlinVersion: String by extra
    dependencies {
        classpath(kotlin("gradle-plugin", kotlinVersion))
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
