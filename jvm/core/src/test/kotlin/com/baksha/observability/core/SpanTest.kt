package com.baksha.observability.core

import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.withContext

class SpanTest {
    private lateinit var collector: TestSpanCollector

    @BeforeTest
    fun setup() {
        collector = TestSpanCollector()
    }

    @Test
    fun `test span creation and ending`() {
        val span = TestSpan("testSpan")
        assertNull(span.duration)
        span.end()
        assertNotNull(span.duration)
    }

    @Test
    fun `test adding attributes to span`() {
        val span = TestSpan("testSpan")
        span.addAttribute("key1", "value1")
        span.addAttribute("key2", 123)
        assertEquals("value1", span.attributes["key1"])
        assertEquals(123, span.attributes["key2"])
    }

    @Test
    fun `test setting error on span`() {
        val span = TestSpan("testSpan")
        val exception = Exception("testException")
        span.recordError(exception)
        assertEquals(exception, span.error)
    }

    @Test
    fun `test withSpan function`() = runTest {
        withContext(SpanCollectorContext(collector)) {
            withSpan("testSpan") { span ->
                assertNotNull(coroutineContext[SpanContext]?.span)
            }
        }
        assertEquals(1, collector.collectedSpans.size)
        assertEquals("testSpan", collector.collectedSpans.first().name)
    }

    @Test
    fun `test withSpanCollector function`() = runTest {
        withSpanCollector(collector) {
            withSpan("testSpan") { span ->
                val activeSpan = coroutineContext[SpanContext]?.span
                assertNotNull(activeSpan)
            }
        }
        assertEquals(1, collector.collectedSpans.size)
        assertEquals("testSpan", collector.collectedSpans.first().name)
    }
}

class TestSpan(override val name: String) : Span {
    private val startTime = TimeSource.Monotonic.markNow()
    var duration: Duration? = null
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

class TestSpanCollector : SpanCollector {
    private val _collectedSpans = mutableListOf<Span>()
    val collectedSpans: List<Span>
        get() = _collectedSpans

    override fun collect(span: Span) {
        _collectedSpans.add(span)
    }
}
