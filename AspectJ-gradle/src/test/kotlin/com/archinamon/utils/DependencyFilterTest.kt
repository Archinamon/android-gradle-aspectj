package com.archinamon.utils

import org.junit.Assert
import org.junit.Test
import java.io.File

/**
* @author Badya on 25.04.2017.
*/

class DependencyFilterTest {

    @Test
    fun testIncluded() {
        Assert.assertTrue(DependencyFilter.isIncludeFilterMatched(file = File("file"), filters = listOf("file")))
    }

    @Test
    fun testNotIncluded() {
        Assert.assertFalse(DependencyFilter.isIncludeFilterMatched(file = File("test"), filters = listOf("file")))
    }

    @Test
    fun testEmptyFiltersNoIncludedFiles() {
        Assert.assertFalse(DependencyFilter.isIncludeFilterMatched(file = File("file"), filters = emptyList()))
    }
}