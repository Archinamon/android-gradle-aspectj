package com.archinamon.plugin

/**
 * TODO: Add description
 *
 * @author archinamon on 12/04/17.
 */

internal enum class ConfigScope(internal val _name: String) {

    STD(AspectJWrapper.CONFIG_STD),
    EXT(AspectJWrapper.CONFIG_EXT),
    TEST(AspectJWrapper.CONFIG_TEST);
}