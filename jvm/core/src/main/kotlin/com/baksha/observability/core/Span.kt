package com.baksha.observability.core

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

val syncSpanThreadLocal = ThreadLocal<Span?>()

public val currentSyncSpan: Span?
    get() = syncSpanThreadLocal.get()

public interface Span {
    public val name: String
    public val attributes: Map<String, Any>
    public val error: Throwable?
    public val parent: Span?
    public fun end()
    public fun addAttribute(key: String, value: Any)
    public fun recordError(error: Throwable)
}

public data class SimpleSpan(
    override val name: String,
    override val parent: Span? = null
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
    public fun start(span: Span)
}

object GlobalTracer {
    @Volatile private var collector: SpanCollector? = null

    fun registerIfAbsent(newCollector: SpanCollector) {
        if (collector == null) {
            synchronized(this) {
                if (collector == null) {
                    collector = newCollector
                }
            }
        }
    }

    internal fun clearForTesting() {
        collector = null
    }

    fun get(): SpanCollector {
        return collector ?: error("GlobalTracer collector is not initialized.")
    }
}


public class PrinterSpanCollector(private val output: (String) -> Unit = ::println) : SpanCollector {
    override fun start(span: Span) {
        output("Span collected: ${span.name}, Parent: ${span.parent?.name}, Attributes: ${span.attributes}, Error: ${span.error}")
    }
}

public class CompositeSpanCollector(private vararg val collectors: SpanCollector) : SpanCollector {
    override fun start(span: Span) {
        collectors.forEach { it.start(span) }
    }
}

public class SpanContext(public val span: Span) : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<SpanContext>
}

public suspend inline fun <T> withSpan(
    name: String,
    crossinline block: suspend CoroutineScope.(Span) -> T
): T {
    val parentSpan = coroutineContext[SpanContext]?.span ?: currentSyncSpan
    val span = SimpleSpan(name, parentSpan)
    val spanContext = SpanContext(span)
    return withContext(spanContext) {
        try {
            block(span)
        } catch (e: Throwable) {
            span.recordError(e)
            throw e
        } finally {
            span.end()
            GlobalTracer.get().start(span)
        }
    }
}

public inline fun <T> withSyncSpan(
    name: String,
    block: (Span) -> T
): T {
    val parentSpan = currentSyncSpan
    val span = SimpleSpan(name, parentSpan)
    syncSpanThreadLocal.set(span)
    try {
        return block(span)
    } catch (e: Throwable) {
        span.recordError(e)
        throw e
    } finally {
        span.end()
        GlobalTracer.get().start(span)
        syncSpanThreadLocal.set(parentSpan)
    }
}
