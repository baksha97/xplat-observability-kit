package com.baksha.observability.core.span

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer

/**
 * Provides functionality for capturing execution metrics (duration and errors)
 * while delegating span creation and error handling to the existing functions.
 *
 * This class supports both synchronous and suspending operations,
 * as well as functions that throw exceptions or return a [Result].
 */
abstract class SpanCapturing(val tracer: Tracer) {
    /**
     * Captures a synchronous operation that may throw an exception.
     *
     * @param key Unique identifier for metric collection.
     * @param attributes Optional attributes for the span.
     * @param block The operation to run within the span.
     * @return The result of the operation.
     * @throws Throwable Rethrows any exception thrown by [block].
     */
    inline fun <T> withSpanCapture(
        key: String,
        attributes: Map<String, String> = emptyMap(),
        crossinline block: (Span) -> T
    ): T =
        withSpan(tracer, key, attributes) {
            runCatching { block(it) }
        }.getOrThrow()

    /**
     * Captures a synchronous operation that returns a [Result].
     *
     * @param key Unique identifier for metric collection.
     * @param attributes Optional attributes for the span.
     * @param block The operation to run within the span that returns a [Result].
     * @return The [Result] of the operation.
     */
    inline fun <T> withSpanCaptureResult(
        key: String,
        attributes: Map<String, String> = emptyMap(),
        crossinline block: (Span) -> Result<T>
    ): Result<T> =
        withSpan(tracer, key, attributes) {
            block(it)
        }

    /**
     * Captures a suspending operation that may throw an exception.
     *
     * @param key Unique identifier for metric collection.
     * @param attributes Optional attributes for the span.
     * @param block The suspending operation to run within the span.
     * @return The result of the operation.
     * @throws Throwable Rethrows any exception thrown by [block].
     */
    suspend inline fun <reified T> withSuspendingSpanCapture(
        key: String,
        attributes: Map<String, String> = emptyMap(),
        crossinline block: suspend (Span) -> T
    ): T  =
        withSuspendingSpan(tracer, key, attributes) {
            runCatching { block(it) }
        }.getOrThrow()

    /**
     * Captures a suspending operation that returns a [Result].
     *
     * @param key Unique identifier for metric collection.
     * @param attributes Optional attributes for the span.
     * @param block The suspending operation to run within the span that returns a [Result].
     * @return The [Result] of the operation.
     */
    suspend inline fun <T> withSuspendingSpanCaptureResult(
        key: String,
        attributes: Map<String, String> = emptyMap(),
        crossinline block: suspend (Span) -> Result<T>
    ): Result<T> =
        withSuspendingSpan(tracer, key, attributes) {
            block(it)
        }
}
