package com.archinamon.utils

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AarExploringTest {

    @Rule
    @JvmField
    val folder = TemporaryFolder()

    @Test
    fun noNPEIfFileNotExist() {
        findPackageNameIfAar(File("/build-cache/this/file/not/exist"))
    }
}