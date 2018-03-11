package com.archinamon.utils

import org.junit.Test
import java.io.File

class AarExploringTest {

    @Test
    fun noNPEIfFileNotExist() {
        findPackageNameIfAar(File("/build-cache/this/file/not/exist"))
    }
}