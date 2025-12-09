package com.kormax.felicatool.annotation

/**
 * Annotation to mark a sealed class for automatic enum-like entry generation.
 *
 * When applied to a sealed class, the KSP processor will generate:
 * - An `entries` list containing all data object subclasses
 * - A `knownByValue` map for quick lookup by the specified value property
 * - A `fromValue` function that returns the matching entry or creates an Unknown instance
 *
 * @param valueProperty The name of the property to use as the lookup key (default: "value")
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SealedEnum(val valueProperty: String = "value")
