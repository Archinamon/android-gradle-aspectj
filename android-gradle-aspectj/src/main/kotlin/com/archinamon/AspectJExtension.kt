package com.archinamon

import org.gradle.api.JavaVersion

open class AspectJExtension {

    open var ajc = "1.9.4"
    open var java = JavaVersion.VERSION_1_7

    open var includeAllJars = false
    open var includeJar = mutableSetOf<String>()
    open var excludeJar = mutableSetOf<String>()
    open var extendClasspath = true

    open var includeAspectsFromJar = mutableSetOf<String>()
    open var ajcArgs = mutableSetOf<String>()

    internal open var dryRun = false
    open var compileTests = true

    open var weaveInfo = true
    open var debugInfo = false

    open var addSerialVersionUID = false
    open var noInlineAround = false

    open var ignoreErrors = false
    open var breakOnError = true

    open var experimental = false
    open var buildTimeLog = true

    open var transformLogFile = "ajc-transform.log"
    open var compilationLogFile = "ajc-compile.log"

    fun ajcArgs(vararg args: String): AspectJExtension {
        ajcArgs.addAll(args)
        return this
    }

    fun includeJar(vararg filters: String): AspectJExtension {
        includeJar.addAll(filters)
        return this
    }

    fun excludeJar(vararg filters: String): AspectJExtension {
        excludeJar.addAll(filters)
        return this
    }

    fun includeAspectsFromJar(vararg filters: String): AspectJExtension {
        includeAspectsFromJar.addAll(filters)
        return this
    }
}
