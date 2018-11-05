package com.archinamon.utils

import com.android.build.api.transform.JarInput
import java.io.File

/**
 * Jar/aar filter that able to search within gradle cache for asked package name in aar's manifests
 *
 * @author archinamon on 18/03/17.
 */

internal object DependencyFilter {

    private enum class Policy {
        INCLUDE,
        EXCLUDE
    }

    internal fun isExcludeFilterMatched(jar: JarInput, filters: Collection<String>?): Boolean {
        return isExcludeFilterMatched(jar.file, filters) || filters?.any { isContained(jar.name, it) } == true
    }

    internal fun isIncludeFilterMatched(jar: JarInput, filters: Collection<String>?): Boolean {
        return isIncludeFilterMatched(jar.file, filters) || filters?.any { isContained(jar.name, it) } == true
    }

    internal fun isExcludeFilterMatched(file: File?, filters: Collection<String>?): Boolean {
        return isFilterMatched(file, filters, Policy.EXCLUDE)
    }

    internal fun isIncludeFilterMatched(file: File?, filters: Collection<String>?): Boolean {
        return isFilterMatched(file, filters, Policy.INCLUDE)
    }

    private fun isFilterMatched(file: File?, filters: Collection<String>?, filterPolicy: Policy): Boolean {
        if (file === null) {
            return false
        }

        if (filters === null || filters.isEmpty()) {
            return filterPolicy === Policy.INCLUDE
        }

        val str = findPackageNameIfAar(file)
        return filters.any { isContained(str, it) }
    }

    private fun isContained(str: String?, filter: String): Boolean {
        if (str === null) {
            return false
        }

        return when {
            str.contains(filter) -> true
            filter.contains("/") -> str.contains(filter.replace("/", File.separator))
            filter.contains("\\") -> str.contains(filter.replace("\\", File.separator))
            else -> false
        }
    }
}