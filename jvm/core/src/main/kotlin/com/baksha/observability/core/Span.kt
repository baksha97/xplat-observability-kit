package com.baksha.observability.core

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope

public interface Span {
    public val name: String
    public val attributes: Map<String, Any>
    public val error: Throwable?
    public val parent: Span? // Add parent property
    public fun end()
    public fun addAttribute(key: String, value: Any)
    public fun recordError(error: Throwable)
}

public data class SimpleSpan(
    override val name: String,
    override val parent: Span? = null // Add parent property
) : Span {
    private val startTime = TimeSource.Monotonic.markNow()
    private var duration: Duration? = null
    override val attributes: MutableMap<String, Any> = mutableMapOf()
    override var error: Throwable? = null

    override fun end() {
        duration = startTime.elapsedNow()
    }

    override fun addAttribute(key: String, value: Any) {
        attributes[key] = value
    }

    override fun recordError(error: Throwable) {
        this.error = error
    }
}

public interface SpanCollector {
    public fun collect(span: Span)
}

public class SpanContext(public val span: Span) : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<SpanContext>
}

public class SpanCollectorContext(public val collector: SpanCollector) : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<SpanCollectorContext>
}

public class PrinterSpanCollector(private val output: (String) -> Unit = ::println) : SpanCollector {
    override fun collect(span: Span) {
        output("Span collected: \\${span.name}, Parent: \\${span.parent?.name}, Attributes: \\${span.attributes}, Error: \\${span.error}")
    }
}

public class CompositeSpanCollector(private vararg val collectors: SpanCollector) : SpanCollector {
    override fun collect(span: Span) {
        collectors.forEach { it.collect(span) }
    }
}

public suspend inline fun <T> withSpan(
    name: String,
    crossinline block: suspend CoroutineScope.(Span) -> T
): T {
    val parentSpan = coroutineContext[SpanContext]?.span
    val span = SimpleSpan(name, parentSpan) // Set parent span
    val spanContext = SpanContext(span)
    return withContext(spanContext) {
        try {
            block(span)
        } catch (e: Throwable) {
            span.recordError(e)
            throw e
        } finally {
            span.end()
            val collectorContext = coroutineContext[SpanCollectorContext]
            collectorContext?.collector?.collect(span)
        }
    }
}

public suspend inline fun withSpanCollector(
    collector: SpanCollector,
    crossinline block: suspend CoroutineScope.(SpanCollector) -> Unit
) {
    val collectorContext = SpanCollectorContext(collector)
    withContext(collectorContext) {
        block(collector)
    }
}
