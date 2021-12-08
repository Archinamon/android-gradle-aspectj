package com.archinamon.utils

import org.gradle.internal.impldep.jakarta.xml.bind.JAXBContext
import org.gradle.internal.impldep.jakarta.xml.bind.annotation.XmlAccessType
import org.gradle.internal.impldep.jakarta.xml.bind.annotation.XmlAccessorType
import org.gradle.internal.impldep.jakarta.xml.bind.annotation.XmlAttribute
import org.gradle.internal.impldep.jakarta.xml.bind.annotation.XmlRootElement
import java.io.File

internal fun findPackageNameIfAar(input: File): String {
    if (!input.absolutePath.contains("build-cache")) return input.absolutePath
    if (!input.exists()) return "[empty]"

    var f: File? = input

    do {
        f = f?.parentFile
    } while (f?.isDirectory!! && f.listFiles()?.any(::findManifest) == false)

    val manifest = f.listFiles()?.find(::findManifest)
    if (manifest != null) {
        val xml = readXml(manifest, Manifest::class.java)
        return xml.libPackage
    }

    return input.name
}

private fun findManifest(f: File): Boolean {
    return f.name.equals("androidmanifest.xml", true)
}

private inline fun <reified T> readXml(file: File, clazz: Class<T>): T {
    val jc = JAXBContext.newInstance(clazz)
    val unmarshaller = jc.createUnmarshaller()
    val data = unmarshaller.unmarshal(file) ?: error("Marshalling failed. Get null object")
    return data as T
}

@XmlRootElement(name = "manifest")
@XmlAccessorType(XmlAccessType.FIELD)
internal class Manifest {

    @XmlAttribute(name = "package")
    lateinit var libPackage: String
}