package com.baksha.observability.core.trace

/**
 * Used to apply to an interface to generate monitoring code & a tracing extension
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Traceable {
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Span(
        // The name of the span to be created
        val name: String,
        // The parameters to be captured as attributes of the span event
        val captureParameters: Array<String> = [],
        // Additional context to be provided from the instance attribute's live value
        val additionalContextFromAttributes: Array<String> = [],
    )
}

