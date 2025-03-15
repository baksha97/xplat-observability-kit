package com.baksha.observability.tests

import com.baksha.observability.core.*
import com.baksha.observability.core.span.withSpan
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TestSpanCollector : SpanCollector {
    data class CollectedSpan(
        val name: String,
        val parentName: String?,
        val attributes: Map<String, Any>,
        val error: Throwable?
    )

    val spans = mutableListOf<CollectedSpan>()

    override fun start(span: Span) {
        spans.add(
            CollectedSpan(
                name = span.name,
                parentName = span.parent?.name,
                attributes = span.attributes,
                error = span.error
            )
        )
    }

    fun clear() = spans.clear()
}

class SpanPropagationTest {

    private val testCollector = TestSpanCollector()

    @BeforeTest
    fun setup() {
        GlobalTracer.registerIfAbsent(testCollector)
    }

    @AfterTest
    fun tearDown() {
        GlobalTracer.reset()
        testCollector.clear()
    }

    @Test
    fun testSyncSpanPropagation() {
        withSyncSpan("SyncParent") { parent ->
            parent.addAttribute("type", "sync")

            withSyncSpan("SyncChild") { child ->
                child.addAttribute("childTask", "queryDB")
            }
        }

        assertEquals(2, testCollector.spans.size)
        assertEquals("SyncChild", testCollector.spans[0].name)
        assertEquals("SyncParent", testCollector.spans[0].parentName)
        assertEquals("SyncParent", testCollector.spans[1].name)
        assertNull(testCollector.spans[1].parentName)
    }

    @Test
    fun testAsyncSpanPropagation() = runTest {
        withSpan("AsyncParent") { parent ->
            parent.addAttribute("type", "async")

            coroutineScope {
                launch {
                    withSpan("AsyncChild1") { child1 ->
                        child1.addAttribute("task", "fetchData")
                        delay(10)
                    }
                }

                launch {
                    withSpan("AsyncChild2") { child2 ->
                        child2.addAttribute("task", "writeFile")
                        delay(20)
                    }
                }
            }
        }

        assertEquals(3, testCollector.spans.size)

        val child1 = testCollector.spans.find { it.name == "AsyncChild1" }
        val child2 = testCollector.spans.find { it.name == "AsyncChild2" }
        val parent = testCollector.spans.find { it.name == "AsyncParent" }

        assertNotNull(child1)
        assertNotNull(child2)
        assertNotNull(parent)

        assertEquals("AsyncParent", child1.parentName)
        assertEquals("AsyncParent", child2.parentName)
        assertNull(parent.parentName)
    }

    @Test
    fun testSpanErrorRecording() {
        val exception = assertFailsWith<IllegalArgumentException> {
            withSyncSpan("ErrorSpan") {
                throw IllegalArgumentException("Test error")
            }
        }

        assertEquals("Test error", exception.message)
        assertEquals(1, testCollector.spans.size)
        val errorSpan = testCollector.spans.first()
        assertEquals("ErrorSpan", errorSpan.name)
        assertEquals("Test error", errorSpan.error?.message)
    }

    @Test
    fun testMixedCoroutineAndSyncSpans() = runTest {
        withSpan("MixedParent") { parent ->
            parent.addAttribute("mixed", true)

            withSyncSpan("NestedSyncChild") { syncChild ->
                syncChild.addAttribute("task", "syncNested")
            }

            launch {
                withSpan("NestedAsyncChild") { asyncChild ->
                    asyncChild.addAttribute("task", "asyncNested")
                }
            }
        }

        assertEquals(3, testCollector.spans.size)

        val syncChild = testCollector.spans.find { it.name == "NestedSyncChild" }
        val asyncChild = testCollector.spans.find { it.name == "NestedAsyncChild" }
        val parent = testCollector.spans.find { it.name == "MixedParent" }

        assertNotNull(syncChild)
        assertNotNull(asyncChild)
        assertNotNull(parent)

        assertEquals("MixedParent", syncChild.parentName)
        assertEquals("MixedParent", asyncChild.parentName)
        assertNull(parent.parentName)
    }
}
