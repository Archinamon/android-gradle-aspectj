package com.archinamon.utils

import java.io.File

/**
 * TODO: Add description
 *
 * @author archinamon on 18/03/17.
 */

internal object DependencyFilter {

    private enum class Policy {
        INCLUDE,
        EXCLUDE
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

        val filterTmp = filter
        return when {
            str.contains(filterTmp) -> true
            filterTmp.contains("/") -> str.contains(filterTmp.replace("/", File.separator))
            filterTmp.contains("\\") -> str.contains(filterTmp.replace("\\", File.separator))
            else -> false
        }
    }
}