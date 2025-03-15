package com.baksha.observability.core

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.TimeSource

class SpanTest {
    private lateinit var collector: TestSpanCollector

    @BeforeTest
    fun setup() {
        GlobalTracer.clearForTesting() // <-- clear the previous global collector
        collector = TestSpanCollector()
        GlobalTracer.registerIfAbsent(collector)
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
        withSpan("testSpan") { span ->
            assertNotNull(coroutineContext[SpanContext]?.span)
        }
        assertEquals(1, collector.collectedSpans.size)
        assertEquals("testSpan", collector.collectedSpans.first().name)
    }

    @Test
    fun `test nested spans`() = runTest {
        withSpan("parentSpan") { parentSpan ->
            withSpan("childSpan") { childSpan ->
                assertEquals(parentSpan, childSpan.parent)
            }
        }
        assertEquals(2, collector.collectedSpans.size)
        val parentSpan = collector.collectedSpans.find { it.name == "parentSpan" }
        val childSpan = collector.collectedSpans.find { it.name == "childSpan" }
        assertNotNull(parentSpan)
        assertNotNull(childSpan)
        assertEquals(parentSpan, childSpan?.parent)
    }

    @Test
    fun `test multiple levels of nested spans`() = runTest {
        withSpan("grandparentSpan") { grandparentSpan ->
            withSpan("parentSpan") { parentSpan ->
                withSpan("childSpan") { childSpan ->
                    assertEquals(grandparentSpan, parentSpan.parent)
                    assertEquals(parentSpan, childSpan.parent)
                }
            }
        }
        assertEquals(3, collector.collectedSpans.size)
    }

    @Test
    fun `test mixing synchronous and asynchronous spans`() = runTest {
        withSyncSpan("syncParent") { syncParent ->
            withSpan("asyncChild") { asyncChild ->
                assertEquals(syncParent, asyncChild.parent)
            }
        }
        assertEquals(2, collector.collectedSpans.size)
        val syncParent = collector.collectedSpans.first { it.name == "syncParent" }
        val asyncChild = collector.collectedSpans.first { it.name == "asyncChild" }
        assertEquals(syncParent, asyncChild.parent)
    }


    @Test
    fun `test CompositeSpanCollector`() {
        val testCollector1 = TestSpanCollector()
        val testCollector2 = TestSpanCollector()
        val compositeCollector = CompositeSpanCollector(testCollector1, testCollector2)
        val span = TestSpan("testSpan")
        compositeCollector.start(span)
        assertEquals(1, testCollector1.collectedSpans.size)
        assertEquals(1, testCollector2.collectedSpans.size)
        assertEquals("testSpan", testCollector1.collectedSpans.first().name)
        assertEquals("testSpan", testCollector2.collectedSpans.first().name)
    }
}

class TestSpan(
    override val name: String,
    override val parent: Span? = null
) : Span {
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

    override fun start(span: Span) {
        _collectedSpans.add(span)
    }
}
