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
    public fun end()
    public fun addAttribute(key: String, value: Any)
    public fun recordError(error: Throwable)
}

public data class SimpleSpan(override val name: String) : Span {
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

context(SpanCollector)
public suspend inline fun <T> withSpan(
    name: String,
    crossinline block: context(Span) CoroutineScope.() -> T
): T {
    val span = SimpleSpan(name)
    val spanContext = SpanContext(span)
    return withContext(spanContext) {
        try {
            block.invoke(span, this)
        } catch (e: Throwable) {
            span.recordError(e)
            throw e
        } finally {
            span.end()
            this@SpanCollector.collect(span)
        }
    }
}

public suspend inline fun withSpanCollector(
    collector: SpanCollector,
    crossinline block: context(SpanCollector) CoroutineScope.() -> Unit
) {
    withContext(coroutineContext + SpanCollectorContext(collector)) {
        block.invoke(collector, this)
    }
}
