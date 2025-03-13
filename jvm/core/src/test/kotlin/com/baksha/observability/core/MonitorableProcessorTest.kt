package com.baksha.observability.core

import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These tests ignore durationMs since actually testing that is tricky.
 */
class MonitorableProcessorTest {
    private lateinit var collector: TestCollector
    private lateinit var sut: TestInterface

    @BeforeTest
    fun setup() {
        collector = TestCollector()
        sut = TestImplementation(
            nestedOptional = TestNested(),
            nestedRequired = TestNested()
        ).monitored(collector)
    }

    @Test
    fun `test immutable property passthrough works correctly`() {
        // The underlying implementation returns 1
        assertEquals(1, sut.sample)

        // Verify no monitoring data was collected for property access
        assertTrue(collector.collectedData.isEmpty())
    }

    @Test
    fun `test mutable property passthrough works correctly`() {
        // Test initial value
        assertEquals(0, sut.mutating)

        // Test setting new value
        sut.mutating = 42
        assertEquals(42, sut.mutating)

        // Test multiple updates
        sut.mutating++
        assertEquals(43, sut.mutating)

        // Verify no monitoring data was collected for property access or modification
        assertTrue(collector.collectedData.isEmpty())
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
    fun `test successful suspend operation is monitored`() = runTest {
        val input = "TestInput"
        val result = sut.successfulSuspendOperation(input)

        assertEquals(input, result)

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("successful_suspend_operation", key)
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
    fun `test failing suspend operation is monitored`() = runTest {
        val exceptionMessage = "Test suspend exception"
        val testException = IllegalStateException(exceptionMessage)

        val exception = assertFailsWith<IllegalStateException> {
            sut.failingSuspendOperation(testException)
        }
        assertEquals(exceptionMessage, exception.message)

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("failingSuspendOperation", key)
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
    fun `test result succeeding suspend operation is monitored`() = runTest {
        val input = "TestSuspendResult"
        val result = sut.resultSucceedingSuspendOperation(input)

        assertTrue(result.isSuccess)
        assertEquals(input, result.getOrThrow())

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("resultSucceedingSuspendOperation", key)
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

    @Test
    fun `test result failing suspend operation is monitored`() = runTest {
        val exceptionMessage = "Result suspend failure"
        val testException = IllegalArgumentException(exceptionMessage)

        val result = sut.resultFailingSuspendOperation(testException)

        assertTrue(result.isFailure)
        val exception = assertFailsWith<IllegalArgumentException> {
            result.getOrThrow()
        }
        assertEquals(exceptionMessage, exception.message)

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("result_failed_suspend_op", key)
            assertNotNull(exception)
            assertEquals(exceptionMessage, exception.message)
        }
    }

    @Test
    fun `test nested optional interface property passthrough works correctly`() {
        assertEquals(3, sut.nestedOptional?.sample)

        sut.nestedOptional?.mutating = 42
        assertEquals(42, sut.nestedOptional?.mutating)

        assertTrue(collector.collectedData.isEmpty())
    }

    @Test
    fun `test nested optional interface monitored function works`() = runTest {
        val input = "TestNestedInput"
        val result = sut.nestedOptional?.resultSucceedingSuspendOperation(input)

        assertTrue(result?.isSuccess == true)
        assertEquals(input, result?.getOrThrow())

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("nested_result_successful_suspend_op", key)
            assertNull(exception)
        }
    }

    @Test
    fun `test nested required interface property passthrough works correctly`() {
        assertEquals(3, sut.nestedRequired.sample)

        sut.nestedRequired.mutating = 42
        assertEquals(42, sut.nestedRequired.mutating)

        assertTrue(collector.collectedData.isEmpty())
    }

    @Test
    fun `test nested required interface monitored function works`() = runTest {
        val input = "TestNestedInput"
        val result = sut.nestedRequired.resultSucceedingSuspendOperation(input)

        assertTrue(result.isSuccess)
        assertEquals(input, result.getOrThrow())

        assertEquals(1, collector.collectedData.size)
        with(collector.collectedData.first()) {
            assertEquals("nested_result_successful_suspend_op", key)
            assertNull(exception)
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
    var mutating: Int

    val sample: Int?

    val nestedRequired: Nested
    val nestedOptional: Nested?

    @Monitor.Function("successful_operation")
    fun successfulOperation(input: String): String

    @Monitor.Function("successful_suspend_operation")
    suspend fun successfulSuspendOperation(input: String): String

    fun failingOperation(exception: Exception): String

    suspend fun failingSuspendOperation(exception: Exception): String

    fun resultSucceedingOperation(input: String): Result<String>

    suspend fun resultSucceedingSuspendOperation(input: String): Result<String>

    @Monitor.Function("result_failed_op")
    fun resultFailingOperation(exception: Exception): Result<String>

    @Monitor.Function("result_failed_suspend_op")
    suspend fun resultFailingSuspendOperation(exception: Exception): Result<String>

    @Monitor.Collectable
    interface Nested {
        var mutating: Int
        val sample: Int?
        @Monitor.Function("nested_result_successful_suspend_op")
        suspend fun resultSucceedingSuspendOperation(input: String): Result<String>
    }
}

class TestImplementation(
    override val nestedOptional: TestInterface.Nested,
    override val nestedRequired: TestInterface.Nested
): TestInterface {

    override var mutating: Int = 0
    override val sample: Int
        get() = 1

    override fun successfulOperation(input: String): String {
        return input
    }

    override suspend fun successfulSuspendOperation(input: String): String {
        return input
    }

    override fun failingOperation(exception: Exception): String {
        throw exception
    }

    override suspend fun failingSuspendOperation(exception: Exception): String {
        throw exception
    }

    override fun resultSucceedingOperation(input: String): Result<String> {
        return Result.success(input)
    }

    override suspend fun resultSucceedingSuspendOperation(input: String): Result<String> {
        return Result.success(input)
    }

    override fun resultFailingOperation(exception: Exception): Result<String> {
        return Result.failure(exception)
    }

    override suspend fun resultFailingSuspendOperation(exception: Exception): Result<String> {
        return Result.failure(exception)
    }
}

class TestNested: TestInterface.Nested {
    override var mutating: Int = 0
    override val sample: Int = 3
    override suspend fun resultSucceedingSuspendOperation(input: String): Result<String> {
        return Result.success(input)
    }
}