package com.baksha.observability.core

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.TimeSource

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
    fun `test nested spans`() = runTest {
        withSpanCollector(collector) {
            withSpan("parentSpan") { parentSpan ->
                withSpan("childSpan") { childSpan ->
                    assertEquals(parentSpan, childSpan.parent)
                }
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
        withSpanCollector(collector) {
            withSpan("grandparentSpan") { grandparentSpan ->
                withSpan("parentSpan") { parentSpan ->
                    withSpan("childSpan") { childSpan ->
                        assertEquals(grandparentSpan, parentSpan.parent)
                        assertEquals(parentSpan, childSpan.parent)
                    }
                }
            }
        }
        assertEquals(3, collector.collectedSpans.size)
        val grandparentSpan = collector.collectedSpans.find { it.name == "grandparentSpan" }
        val parentSpan = collector.collectedSpans.find { it.name == "parentSpan" }
        val childSpan = collector.collectedSpans.find { it.name == "childSpan" }
        assertNotNull(grandparentSpan)
        assertNotNull(parentSpan)
        assertNotNull(childSpan)
        assertEquals(grandparentSpan, parentSpan?.parent)
        assertEquals(parentSpan, childSpan?.parent)
    }

    @Test
    fun `test PrinterSpanCollector with injected output`() = runTest {
        val output = mutableListOf<String>()
        val printerCollector = PrinterSpanCollector {
            output.add(it)
            println(it)
        }

        withSpanCollector(printerCollector) {
            withSpan("grandparentSpan") { grandparentSpan ->
                withSpan("parentSpan") { parentSpan ->
                    withSpan("childSpan") { childSpan ->
                        assertEquals(grandparentSpan, parentSpan.parent)
                        assertEquals(parentSpan, childSpan.parent)
                    }
                }
            }
        }
        assertEquals(3, output.size)
        assertTrue(output[2].contains("grandparentSpan"))
        assertTrue(output[1].contains("parentSpan"))
        assertTrue(output[0].contains("childSpan"))
    }

    @Test
    fun `test PrinterSpanCollector with nested and parallelized spans`() = runTest {
        val output = mutableListOf<String>()
        val printerCollector = PrinterSpanCollector {
            output.add(it)
            println(it)
        }

        withSpanCollector(printerCollector) {
            withSpan("parentSpan") { parentSpan ->
                val job1 = launch {
                    withSpan("childSpan1") { childSpan1 ->
                        assertEquals(parentSpan, childSpan1.parent)
                    }
                }
                val job2 = launch {
                    withSpan("childSpan2") { childSpan2 ->
                        assertEquals(parentSpan, childSpan2.parent)
                    }
                }
                job1.join()
                job2.join()
            }
        }
        assertEquals(3, output.size)
        assertTrue(output.any { it.contains("parentSpan") })
        assertTrue(output.any { it.contains("childSpan1") })
        assertTrue(output.any { it.contains("childSpan2") })
    }

    @Test
    fun `test CompositeSpanCollector`() {
        val testCollector1 = TestSpanCollector()
        val testCollector2 = TestSpanCollector()
        val compositeCollector = CompositeSpanCollector(testCollector1, testCollector2)
        val span = TestSpan("testSpan")
        compositeCollector.collect(span)
        assertEquals(1, testCollector1.collectedSpans.size)
        assertEquals(1, testCollector2.collectedSpans.size)
        assertEquals("testSpan", testCollector1.collectedSpans.first().name)
        assertEquals("testSpan", testCollector2.collectedSpans.first().name)
    }
}

class TestSpan(
    override val name: String,
    override val parent: Span? = null // Add parent property
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

    override fun collect(span: Span) {
        _collectedSpans.add(span)
    }
}
