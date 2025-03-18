package com.baksha.observability.core.collect

/**
 * Used to apply to an interface to generate monitoring code & a tracing extension
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Collectable {
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Function(
        // The name of the span to be created
        val name: String,
        // The parameters to be captured as attributes of the span event
        val captureParameters: Array<String> = [],
        // Additional context to be provided from the instance attribute's live value
        val additionalContextFromAttributes: Array<String> = [],
    )

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Ignore
}
