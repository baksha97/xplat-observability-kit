package com.baksha.observability.core.trace

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
     * @param block The operation to run within the span.
     * @return The result of the operation.
     * @throws Throwable Rethrows any exception thrown by [block].
     */
    inline fun <T> withSpanCapture(
        key: String,
        crossinline block: (Span) -> T
    ): T =
        withSpan(tracer, key) {
            runCatching { block(it) }
        }.getOrThrow()

    /**
     * Captures a synchronous operation that returns a [Result].
     *
     * @param key Unique identifier for metric collection.
     * @param block The operation to run within the span that returns a [Result].
     * @return The [Result] of the operation.
     */
    inline fun <T> withSpanCaptureResult(
        key: String,
        crossinline block: (Span) -> Result<T>
    ): Result<T> =
        withSpan(tracer, key) {
            block(it)
        }

    /**
     * Captures a suspending operation that may throw an exception.
     *
     * @param key Unique identifier for metric collection.
     * @param block The suspending operation to run within the span.
     * @return The result of the operation.
     * @throws Throwable Rethrows any exception thrown by [block].
     */
    suspend inline fun <T> withSuspendingSpanCapture(
        key: String,
        crossinline block: suspend (Span) -> T
    ): T  =
        withSuspendingSpan(tracer, key) {
            runCatching { block(it) }
        }.getOrThrow()

    /**
     * Captures a suspending operation that returns a [Result].
     *
     * @param key Unique identifier for metric collection.
     * @param block The suspending operation to run within the span that returns a [Result].
     * @return The [Result] of the operation.
     */
    suspend inline fun <T> withSuspendingSpanCaptureResult(
        key: String,
        crossinline block: suspend (Span) -> Result<T>
    ): Result<T> =
        withSuspendingSpan(tracer, key) {
            block(it)
        }
}
