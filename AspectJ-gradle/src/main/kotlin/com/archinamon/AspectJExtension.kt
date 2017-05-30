package com.archinamon

open class AspectJExtension {

    open var ajc = "1.8.10"

    open var includeAllJars = false
    open var includeJar = mutableSetOf<String>()
    open var extendClasspath = true

    open var includeAspectsFromJar = mutableSetOf<String>()
    open var ajcArgs = mutableSetOf<String>()

    open var weaveInfo = true
    open var debugInfo = false

    open var addSerialVersionUID = false
    open var noInlineAround = false

    open var ignoreErrors = false
    open var breakOnError = true

    open var experimental = false

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

    fun includeAspectsFromJar(vararg filters: String): AspectJExtension {
        includeAspectsFromJar.addAll(filters)
        return this
    }
}
