package com.brandonsoto.sample_aidl_library.common

/**
 * Converts this integer to enum type [T]. If this is not possible, then [defaultValue] is returned.
 *
 * @param defaultValue the default value to return if the conversion fails
 * @return a converted integer to enum
 */
inline fun <reified T : Enum<T>> Int?.asEnumOrDefault(defaultValue: T): T {
    return enumValues<T>().firstOrNull { it.ordinal == this } ?: defaultValue
}
