package com.archinamon.utils

import java.io.File
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.*

internal fun findPackageNameIfAar(input: File): String {
    if (!input.absolutePath.contains("build-cache")) return input.absolutePath
    if (!input.exists()) return "[empty]"

    var f: File? = input

    do {
        f = f?.parentFile
    } while (f?.isDirectory!! && !f.listFiles().any(::findManifest))

    val manifest = f.listFiles().find(::findManifest)
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