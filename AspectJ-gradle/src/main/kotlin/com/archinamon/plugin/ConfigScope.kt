package com.archinamon.plugin

/**
 * TODO: Add description
 *
 * @author archinamon on 12/04/17.
 */

internal enum class ConfigScope(internal val _name: String) {

    STD(AspectJPlugin.CONFIG_STD),
    EXT(AspectJPlugin.CONFIG_EXT),
    TEST(AspectJPlugin.CONFIG_TEST);
}