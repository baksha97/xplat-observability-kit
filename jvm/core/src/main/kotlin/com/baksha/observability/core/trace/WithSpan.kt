package com.baksha.observability.core.trace

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
    val span: Span = startSpan(tracer, spanName, attributes)
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
    val span: Span = startSpan(tracer, spanName, attributes)
    return withContext(coroutineContext + span.asContextElement()) {
        return@withContext extractEventsInto(span) { block(span) }
    }
}


fun startSpan(
    tracer: Tracer,
    spanName: String,
    attributes: Map<String, String> = emptyMap(),
): Span {
    val span: Span = tracer
        .spanBuilder(spanName)
        .setParent(Context.current())
        .also { attributes.forEach(it::setAttribute) }
        .startSpan()
    return span
}

inline fun <T> extractEventsInto(
    span: Span,
    block: (span: Span) -> Result<T>
): Result<T> =
    block(span).also {
        span.setStatus(
            if (it.isSuccess) StatusCode.OK
            else StatusCode.ERROR
        )
        it.exceptionOrNull()?.let { e -> span.recordException(e) }
        span.end()
    }