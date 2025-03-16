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
 * Captures a synchronous or ThreadLocal operation that may throw an exception.
 *
 * @param attributes Optional attributes for the span.
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
    val span = tracer
        .spanBuilder(spanName)
        .setParent(Context.current())
        .run {
            attributes.forEach(::setAttribute)
            startSpan()
        }

    span.makeCurrent().use {
        return extractEventsInto(span) { block(span) }
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
        return@withContext extractEventsInto(span) { block(span) }
    }
}


inline fun <T> extractEventsInto(
    span: Span,
    block: (span: Span) -> Result<T>
): Result<T> {
    val result = block(span)
    span.setStatus(
        if (result.isSuccess) StatusCode.OK
        else StatusCode.ERROR
    )
    result.exceptionOrNull()?.let { span.recordException(it) }
    span.end()
    return result
}