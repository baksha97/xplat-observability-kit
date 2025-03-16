package com.baksha.observability.core.span

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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

/**
 * Captures a synchronous or ThreadLocal operation that may throw an exception.
 *
 * @param key Unique identifier for metric collection.
 * @param attributes Optional attributes for the span.
 * @param parent The parent context (defaults to current).
 * @param block The operation to run within the span.
 * @return The result of the operation.
 * @throws Throwable Rethrows any exception thrown by [block].
 */
inline fun <T> withSpan(
    tracer: Tracer,
    spanName: String,
    attributes: Map<String, String> = emptyMap(),
    crossinline block: (Span) -> Result<T>
): Result<T> {
    val parent: Context = Context.current()

    val span = tracer
        .spanBuilder(spanName)
        .setParent(parent)
        .run {
            attributes.forEach(::setAttribute)
            startSpan()
        }

    span.makeCurrent().use {
        val result = block(span)
        span.setStatus(
            if (result.isSuccess) StatusCode.OK
            else StatusCode.ERROR
        )
        result.exceptionOrNull()?.let { span.recordException(it) }
        span.end()
        return result
    }
}


suspend inline fun <T> withSuspendingSpan(
    tracer: Tracer,
    spanName: String,
    attributes: Map<String, String> = emptyMap(),
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (span: Span) -> Result<T>
): Result<T> {
    val span: Span = tracer.spanBuilder(spanName).run {
        attributes.forEach(::setAttribute)
        startSpan()
    }
    return withContext(coroutineContext + span.asContextElement()) {
        val result = block(span)
        span.setStatus(
            if (result.isSuccess) StatusCode.OK
            else StatusCode.ERROR
        )
        result.exceptionOrNull()?.let { span.recordException(it) }
        span.end()
        return@withContext result
    }
}
