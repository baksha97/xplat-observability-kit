package com.baksha.observability.core

import kotlin.test.*

/**
 * These tests ignore durationMs since actually testing that is tricky.
 */
class MonitorableProcessorTest {
    private lateinit var collector: TestCollector
    private lateinit var sut: TestInterface

    @BeforeTest
    fun setup() {
        collector = TestCollector()
        sut = TestImplementation()
            .monitored(collector)
    }

    @Test
    fun `test successful operation is monitored`() {
        val input = "TestInput"
        val result = sut.successfulOperation(input)

        assertEquals(input, result)

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("successful_operation", key)
            assertNull(exception)
        }
    }

    @Test
    fun `test failing operation is monitored`() {
        val exceptionMessage = "Test exception"
        val testException = IllegalStateException(exceptionMessage)

        val exception = assertFailsWith<IllegalStateException> {
            sut.failingOperation(testException)
        }
        assertEquals(exceptionMessage, exception.message)

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("failingOperation", key)
            assertNotNull(exception)
            assertEquals(exceptionMessage, exception.message)
        }
    }

    @Test
    fun `test result succeeding operation is monitored`() {
        val input = "TestResult"
        val result = sut.resultSucceedingOperation(input)

        assertTrue(result.isSuccess)
        assertEquals(input, result.getOrThrow())

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("resultSucceedingOperation", key)
            assertNull(exception)
        }
    }

    @Test
    fun `test result failing operation is monitored`() {
        val exceptionMessage = "Result failure"
        val testException = IllegalArgumentException(exceptionMessage)

        val result = sut.resultFailingOperation(testException)

        assertTrue(result.isFailure)
        val exception = assertFailsWith<IllegalArgumentException> {
            result.getOrThrow()
        }
        assertEquals(exceptionMessage, exception.message)

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("result_failed_op", key)
            assertNotNull(exception)
            assertEquals(exceptionMessage, exception.message)
        }
    }
}

class TestCollector : Monitor.Collector {
    private val _collectedData = mutableListOf<Monitor.Data>()

    val collectedData: List<Monitor.Data>
        get() = _collectedData

    override fun collect(data: Monitor.Data) {
        _collectedData.add(data)
    }
}


@Monitor.Collectable
interface TestInterface {
    @Monitor.Function("successful_operation")
    fun successfulOperation(input: String): String
    fun failingOperation(exception: Exception): String
    fun resultSucceedingOperation(input: String): Result<String>
    @Monitor.Function("result_failed_op")
    fun resultFailingOperation(exception: Exception): Result<String>
}

class TestImplementation : TestInterface {
    override fun successfulOperation(input: String): String {
        return input
    }

    override fun failingOperation(exception: Exception): String {
        throw exception
    }

    override fun resultSucceedingOperation(input: String): Result<String> {
        return Result.success(input)
    }

    override fun resultFailingOperation(exception: Exception): Result<String> {
        return Result.failure(exception)
    }
}
