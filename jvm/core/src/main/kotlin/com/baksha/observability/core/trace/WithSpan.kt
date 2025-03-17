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
 * Provides functionality for capturing execution metrics (duration and errors).
 * This class supports java and kotlin synchronous operations by automatically
 * propagating the current span relationships via [ThreadLocal] storage.
 */
inline fun <T> withSpan(
    tracer: Tracer,
    spanName: String,
    crossinline block: (Span) -> Result<T>
): Result<T> {
    val span: Span = startSpan(tracer, spanName)
    span.makeCurrent().use {
        return extractEventsInto(span) { block(span) }
    }
}

/**
 * Provides functionality for capturing execution metrics (duration and errors).
 * This class supports kotlin suspending operations by automatically
 * propagating the current span relationships via [CoroutineContext] storage.
 */
suspend inline fun <T> withSuspendingSpan(
    tracer: Tracer,
    spanName: String,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (span: Span) -> Result<T>
): Result<T> {
    val span: Span = startSpan(tracer, spanName)
    return withContext(coroutineContext + span.asContextElement()) {
        extractEventsInto(span) { block(span) }
    }
}

/**
 * Internal helpers that cannot be private due to Kotlin's visibility rules and usage in
 * inline functions.
 */
fun startSpan(
    tracer: Tracer,
    spanName: String,
): Span {
    val span: Span = tracer
        .spanBuilder(spanName)
        .setParent(Context.current())
        .startSpan()
    return span
}

inline fun <T> extractEventsInto(
    span: Span,
    block: (span: Span) -> Result<T>
): Result<T> =
    block(span).also { result ->
        span.setStatus(
            if (result.isSuccess) StatusCode.OK
            else StatusCode.ERROR
        )
        result
            .exceptionOrNull()
            ?.let { span.recordException(it) }
        span.end()
    }