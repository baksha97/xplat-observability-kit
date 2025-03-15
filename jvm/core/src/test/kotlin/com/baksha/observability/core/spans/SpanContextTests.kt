// FILE: SpanContextTest.kt

package com.baksha.observability.core

import com.baksha.observability.core.span.withSpan
import kotlinx.coroutines.Dispatchers
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SpanContextTest {

    // A simple in-memory collector for verifying which spans got started.
    class MemorySpanCollector : SpanCollector {
        val collectedSpans = mutableListOf<Span>()
        override fun start(span: Span) {
            collectedSpans.add(span)
        }
    }

    @BeforeTest
    fun setUp() {
        // Reset the global tracer before each test so we have a predictable state.
        GlobalTracer.reset()
        ThreadLocalScope.currentSyncSpan = null
    }

    @AfterTest
    fun tearDown() {
        // Clean up again after each test
        GlobalTracer.reset()
        ThreadLocalScope.currentSyncSpan = null
    }

    @Test
    fun testWithSyncSpan_basic() {
        // Register a memory collector so we can see which spans get started
        val collector = MemorySpanCollector()
        GlobalTracer.registerIfAbsent(collector)

        assertNull(ThreadLocalScope.currentSyncSpan, "No span should be set before we start")

        val result = withSyncSpan("syncRootSpan") { span ->
            assertEquals("syncRootSpan", span.name)
            assertSame(span, ThreadLocalScope.currentSyncSpan, "ThreadLocal should match the new span")
            "someResult"
        }

        // After withSyncSpan, the thread-local should be restored
        assertNull(ThreadLocalScope.currentSyncSpan, "ThreadLocal should be cleared after the block")

        // The return value from the block is returned by withSyncSpan
        assertEquals("someResult", result)

        // The collector should have exactly one span
        assertEquals(1, collector.collectedSpans.size)
        assertEquals("syncRootSpan", collector.collectedSpans.first().name)
    }

    @Test
    fun testWithSyncSpan_propagatesParent() {
        val collector = MemorySpanCollector()
        GlobalTracer.registerIfAbsent(collector)

        withSyncSpan("parentSpan") { parent ->
            // Nesting a new withSyncSpan
            withSyncSpan("childSpan") { child ->
                assertEquals("parentSpan", child.parent?.name)
            }
        }

        // Confirm we have 2 spans, each ended, with correct relationships
        assertEquals(2, collector.collectedSpans.size)
        val childSpan = collector.collectedSpans.find { it.name == "childSpan" }!!
        assertEquals("parentSpan", childSpan.parent?.name)
    }

    @Test
    fun testWithSpan_inCoroutines() = runBlocking {
        val collector = MemorySpanCollector()
        GlobalTracer.registerIfAbsent(collector)

        // Initially, there's no thread-local span
        assertNull(ThreadLocalScope.currentSyncSpan)

        // This will create a new span in the coroutine context.
        withSpan("coroutineSpan") { span ->
            assertEquals("coroutineSpan", span.name)
            // Because of SpanContextElement, we also expect the thread-local to match inside the coroutine
            assertSame(span, ThreadLocalScope.currentSyncSpan)
            "someCoroutineResult"
        }

        // After the block, the thread-local is reset
        assertNull(ThreadLocalScope.currentSyncSpan)

        // Check that the collector recorded the span
        assertEquals(1, collector.collectedSpans.size)
        assertEquals("coroutineSpan", collector.collectedSpans[0].name)
    }

    @Test
    fun testWithSpan_parentComesFromThreadLocal() = runBlocking {
        val collector = MemorySpanCollector()
        GlobalTracer.registerIfAbsent(collector)

        // Suppose we already have a sync span set from legacy code:
        withSyncSpan("syncRootSpan") { syncRoot ->
            // Now inside the synchronous block, we call an async function
            withSpan("childCoroutineSpan") { childSpan ->
                // The parent of childSpan is the thread-local syncRootSpan
                assertEquals(syncRoot, childSpan.parent, "Child should inherit from the thread-local syncRootSpan")
            }
        }

        // We should see 2 spans in total
        assertEquals(2, collector.collectedSpans.size, "Should have collected syncRootSpan + childCoroutineSpan")
        val child = collector.collectedSpans.firstOrNull { it.name == "childCoroutineSpan" }
        val root = collector.collectedSpans.firstOrNull { it.name == "syncRootSpan" }
        assertNotNull(child)
        assertNotNull(root)
        assertSame(root, child?.parent, "The child's parent is the 'syncRootSpan'")
    }

    @Test
    fun testWithSpan_nestedSyncSpanInsideCoroutine() = runBlocking {
        val collector = MemorySpanCollector()
        GlobalTracer.registerIfAbsent(collector)

        withSpan("outerCoroutineSpan") { outer ->
            // We are in a coroutine-based span, let's call a legacy function that uses withSyncSpan
            withSyncSpan("innerSyncSpan") { inner ->
                // The parent of inner should be 'outer'
                assertEquals("outerCoroutineSpan", inner.parent?.name)
                // The thread-local now has 'inner'
                assertEquals("innerSyncSpan", ThreadLocalScope.currentSyncSpan?.name)
            }
            // After the sync block, we revert to the outer coroutine's span in ThreadLocal
            assertSame(outer, ThreadLocalScope.currentSyncSpan)
        }

        // After everything, thread-local is cleared
        assertNull(ThreadLocalScope.currentSyncSpan)

        // We should see 2 spans in the collector
        assertEquals(2, collector.collectedSpans.size)
        val outerSpan = collector.collectedSpans.find { it.name == "outerCoroutineSpan" }!!
        val innerSpan = collector.collectedSpans.find { it.name == "innerSyncSpan" }!!
        assertEquals(outerSpan, innerSpan.parent)
    }

    @Test
    fun testWithSpan_errorIsRecorded() {
        val collector = MemorySpanCollector()
        GlobalTracer.registerIfAbsent(collector)

        val ex = assertFailsWith<IllegalStateException> {
            withSyncSpan("failingSpan") { span ->
                throw IllegalStateException("something went wrong")
            }
        }

        // Verify the collector sees the error
        assertEquals("something went wrong", ex.message)
        assertEquals(1, collector.collectedSpans.size)
        assertEquals("failingSpan", collector.collectedSpans[0].name)
        assertTrue(collector.collectedSpans[0].error is IllegalStateException)
    }

    @Test
    fun testWithSpan_errorIsRecorded_inCoroutine() = runBlocking {
        val collector = MemorySpanCollector()
        GlobalTracer.registerIfAbsent(collector)

        val ex = assertFailsWith<ArithmeticException> {
            withSpan("asyncSpanWithError") {
                throw ArithmeticException("divide by zero")
            }
        }

        assertEquals("divide by zero", ex.message)
        assertEquals(1, collector.collectedSpans.size)
        val span = collector.collectedSpans[0]
        assertEquals("asyncSpanWithError", span.name)
        assertTrue(span.error is ArithmeticException)
    }

    @Test
    fun testThreadSwitchInCoroutineContext() = runBlocking {
        val collector = MemorySpanCollector()
        GlobalTracer.registerIfAbsent(collector)

        // Start with a sync span on the main thread
        withSyncSpan("syncRootSpan") { syncSpan ->
            // Switch to a default dispatcher (which uses a shared pool)
            withContext(Dispatchers.Default) {
                withSpan("childCoroutineSpan") { childSpan ->
                    // The parent should be the thread-local sync span, thanks to ThreadContextElement
                    assertSame(syncSpan, childSpan.parent)

                    // Force another context switch, e.g. to IO
                    withContext(Dispatchers.IO) {
                        // We are now presumably on a different thread
                        // The thread-local should still point to the same childSpan
                        assertSame(childSpan, ThreadLocalScope.currentSyncSpan)
                    }

                    // And back to the Default dispatcher
                    assertSame(childSpan, ThreadLocalScope.currentSyncSpan)
                }
            }
        }

        // Finally, confirm everything was captured
        assertEquals(2, collector.collectedSpans.size)
        val root = collector.collectedSpans.first { it.name == "syncRootSpan" }
        val child = collector.collectedSpans.first { it.name == "childCoroutineSpan" }
        assertSame(root, child.parent)
    }

}
