package com.baksha.observability.core.span

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

fun exceptionIsError(throwable: Throwable): Boolean = throwable !is CancellationException
/**
 * Provides functionality for capturing execution metrics (duration and errors)
 * while delegating span creation and error handling to the existing functions.
 *
 * This class supports both synchronous and suspending operations,
 * as well as functions that throw exceptions or return a [Result].
 */
abstract class SpanCapturing(val tracer: Tracer) {

    val exceptionIsError: (Throwable) -> Boolean = { it !is CancellationException }

    /**
     * Captures a synchronous operation that may throw an exception.
     *
     * @param key Unique identifier for metric collection.
     * @param attributes Optional attributes for the span.
     * @param parent The parent context (defaults to current).
     * @param exceptionIsError Predicate to decide if an exception should be recorded as error.
     * @param block The operation to run within the span.
     * @return The result of the operation.
     * @throws Throwable Rethrows any exception thrown by [block].
     */
    inline fun <T> withSpanCapture(
        key: String,
        attributes: Map<String, String> = emptyMap(),
        parent: Context = Context.current(),
        crossinline block: (Span) -> T
    ): T =
        withSpan(tracer, key, attributes, parent, exceptionIsError) {
             block(it)
        }

    /**
     * Captures a synchronous operation that returns a [Result].
     *
     * @param key Unique identifier for metric collection.
     * @param attributes Optional attributes for the span.
     * @param parent The parent context (defaults to current).
     * @param exceptionIsError Predicate to decide if an exception should be recorded as error.
     * @param block The operation to run within the span that returns a [Result].
     * @return The [Result] of the operation.
     */
    inline fun <T> withSpanCaptureResult(
        key: String,
        attributes: Map<String, String> = emptyMap(),
        parent: Context = Context.current(),
        crossinline exceptionIsError: (Throwable) -> Boolean = { it !is CancellationException },
        crossinline block: (Span) -> Result<T>
    ): Result<T> =
        withSpan(tracer, key, attributes, parent, exceptionIsError) {
            block(it)
        }

    /**
     * Captures a suspending operation that may throw an exception.
     *
     * @param key Unique identifier for metric collection.
     * @param attributes Optional attributes for the span.
     * @param exceptionIsError Predicate to decide if an exception should be recorded as error.
     * @param block The suspending operation to run within the span.
     * @return The result of the operation.
     * @throws Throwable Rethrows any exception thrown by [block].
     */
    suspend inline fun <T> withSuspendingSpanCapture(
        key: String,
        attributes: Map<String, String> = emptyMap(),
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        crossinline block: suspend (Span) -> T
    ): T  =
        withSuspendingSpan(tracer, key, attributes, coroutineContext) {
             block(it)
        }

    /**
     * Captures a suspending operation that returns a [Result].
     *
     * @param key Unique identifier for metric collection.
     * @param attributes Optional attributes for the span.
     * @param exceptionIsError Predicate to decide if an exception should be recorded as error.
     * @param block The suspending operation to run within the span that returns a [Result].
     * @return The [Result] of the operation.
     */
    suspend inline fun <T> withSuspendingSpanCaptureResult(
        key: String,
        attributes: Map<String, String> = emptyMap(),
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        crossinline block: suspend (Span) -> Result<T>
    ): Result<T> =
        withSuspendingSpan(tracer, key, attributes, coroutineContext) {
            block(it)
        }
}


/**
 * OTEL SDK API
 * Sync/Java `withSpan`
 */
inline fun <T> withSpan(
    tracer: Tracer,
    spanName: String,
    attributes: Map<String, String> = emptyMap(),
    parent: Context = Context.current(),
    crossinline exceptionIsError: (Throwable) -> Boolean,
    crossinline block: (Span) -> T
): T {
    val span = tracer
        .spanBuilder(spanName)
        .setParent(parent)
        .run {
            attributes.forEach(::setAttribute)
            startSpan()
        }

    span.makeCurrent().use {
        try {
            return block(span)
        }
        catch (throwable: Throwable) {
            if (exceptionIsError(throwable)) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(throwable)
            }
            throw throwable
        }
        finally {
            span.end()
        }
    }
}


suspend inline fun <T> withSuspendingSpan(
    tracer: Tracer,
    spanName: String,
    attributes: Map<String, String> = emptyMap(),
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (span: Span) -> T
): T {
    val span: Span = tracer.spanBuilder(spanName).run {
        attributes.forEach(::setAttribute)
        startSpan()
    }
    return withContext(coroutineContext + span.asContextElement()) {
        try {
            block(span)
        }
        catch (throwable: Throwable) {
            if (exceptionIsError(throwable)) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(throwable)
            }
            throw throwable
        }
        finally {
            span.end()
        }
    }
}
