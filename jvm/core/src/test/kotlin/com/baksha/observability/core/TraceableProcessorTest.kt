package com.baksha.observability.core

import com.baksha.observability.core.trace.Traceable
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/* ===========================================================
   2) The interface under test, annotated with @Traceable
      - Includes nested interface
   =========================================================== */
@Traceable
interface SomeService {
    var mutating: Int
    val sample: Int?

    val nestedRequired: Nested
    val nestedOptional: Nested?

    // Simple function with a custom span name
    @Traceable.Span(name = "getUser")
    fun getUser(id: String): String

    // Another function with custom name
    @Traceable.Span(
        name = "validateCredentialszz",
        captureParameters = ["username", "password"],
        additionalContextFromAttributes = ["sample"]
    )
    fun validateCredentials(username: String, password: String): Result<Boolean>

    // No explicit span annotation => fallback to function name
    fun successfulOperation(input: String): String

    @Traceable.Span(name = "successfulSuspendOperation")
    suspend fun successfulSuspendOperation(input: String): String

    // Fallback to function name
    fun failingOperation(exception: Exception): String
    suspend fun failingSuspendOperation(exception: Exception): String

    // Return a kotlin.Result
    @Traceable.Span(name = "result_succ_op", captureParameters = ["input"], additionalContextFromAttributes = ["sample"])
    fun resultSucceedingOperation(input: String): Result<String>
    suspend fun resultSucceedingSuspendOperation(input: String): Result<String>

    @Traceable.Span(name = "result_failed_op")
    fun resultFailingOperation(exception: Exception): Result<String>

    @Traceable.Span(name = "result_failed_suspend_op")
    suspend fun resultFailingSuspendOperation(exception: Exception): Result<String>

    // Nested interface also annotated with @Traceable
    @Traceable
    interface Nested {
        var mutating: Int
        val sample: Int?

        @Traceable.Span(name = "nested_result_successful_suspend_op")
        suspend fun resultSucceedingSuspendOperation(input: String): Result<String>
    }
}
/* ===========================================================
   6) The test class with extensive coverage
   =========================================================== */
class TraceableProcessorTest {

    private lateinit var inMemoryExporter: InMemorySpanExporter
    private lateinit var tracerProvider: SdkTracerProvider
    private lateinit var openTelemetry: OpenTelemetrySdk
    private lateinit var tracer: Tracer

    @BeforeTest
    fun setup() {
        // Build an in-memory exporter
        inMemoryExporter = InMemorySpanExporter()

        // Create an SdkTracerProvider that sends finished spans to our inMemoryExporter
        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(inMemoryExporter))
            .build()

        // Build OpenTelemetry from that provider
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()

        tracer = openTelemetry.getTracer("TraceableProcessorTest")
    }

    @AfterTest
    fun tearDown() {
        // Flush + shutdown the provider (optional in many test environments)
        tracerProvider.shutdown()
    }

    /* -------------------------------------------------------
       Property passthrough tests
       ------------------------------------------------------- */

    @Test
    fun `test immutable property passthrough works correctly`() {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        // Access the immutable property
        assertEquals(1, sut.sample)

        // Verify no spans were generated
        val spans = inMemoryExporter.getFinishedSpans()
        assertTrue(spans.isEmpty(), "No spans should be generated for property access")
    }

    @Test
    fun `test mutable property passthrough works correctly`() {
        val real: SomeService = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        // Initial value
        assertEquals(0, sut.mutating)

        // Mutate
        sut.mutating = 42
        assertEquals(42, sut.mutating)

        // Verify no spans were generated
        val spans = inMemoryExporter.getFinishedSpans()
        assertTrue(spans.isEmpty(), "No spans should be generated for property mutation")
    }

    /* -------------------------------------------------------
       Simple function tests
       ------------------------------------------------------- */

    @Test
    fun `test getUser`() {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        // Act
        val result = sut.getUser("Alice")
        assertEquals("Alice", result)

        // Inspect the spans
        val spans = inMemoryExporter.getFinishedSpans()
        assertTrue(spans.isNotEmpty())
        val span = spans.first()
        // Should match @Traceable.Span(name = "getUser")
        assertEquals("getUser", span.name)
        assertEquals(StatusCode.OK, span.status.statusCode)

        // No exception event expected
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNull(exceptionEvent, "Should not record an exception event for success")
    }

    @Test
    fun `test successful operation is traced`() {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        // Act
        val input = "TestInput"
        val result = sut.successfulOperation(input)
        assertEquals(input, result)

        // Inspect spans
        val spans = inMemoryExporter.getFinishedSpans()
        assertEquals(1, spans.size)
        val span = spans.first()
        // No custom annotation => fallback to method name
        assertEquals("successfulOperation", span.name)
        assertEquals(StatusCode.OK, span.status.statusCode)
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNull(exceptionEvent, "Should not record an exception event for success")
    }

    @Test
    fun `test failing operation is traced`() {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        val exceptionMessage = "Test exception"
        val testException = IllegalStateException(exceptionMessage)

        try {
            sut.failingOperation(testException)
            fail("Expected an exception to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals(exceptionMessage, e.message)
        }

        val spans = inMemoryExporter.getFinishedSpans()
        assertEquals(1, spans.size)
        val span = spans.first()
        // Fallback to method name
        assertEquals("failingOperation", span.name)
        assertEquals(StatusCode.ERROR, span.status.statusCode)

        // Check that exception event is recorded
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNotNull(exceptionEvent, "Should record an exception event for a failing call")
    }

    /* -------------------------------------------------------
       Suspend function tests
       ------------------------------------------------------- */

    @Test
    fun `test successful suspend operation is traced`() = runTest {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        val input = "TestSuspendInput"
        val result = sut.successfulSuspendOperation(input)
        assertEquals(input, result)

        val spans = inMemoryExporter.getFinishedSpans()
        assertEquals(1, spans.size)
        val span = spans.first()
        // Custom name from @Traceable.Span(name="successfulSuspendOperation")
        assertEquals("successfulSuspendOperation", span.name)
        assertEquals(StatusCode.OK, span.status.statusCode)
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNull(exceptionEvent, "No exception event expected for success")
    }

    @Test
    fun `test failing suspend operation`() = runTest {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        val exceptionMessage = "test exception"

        try {
            sut.failingSuspendOperation(IllegalStateException(exceptionMessage))
            fail("Expected an exception to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals(exceptionMessage, e.message)
        }

        val spans = inMemoryExporter.getFinishedSpans()
        assertEquals(1, spans.size)
        val span = spans.first()
        // By default, no custom name => fallback to method name
        assertEquals("failingSuspendOperation", span.name)
        // Should be an error
        assertEquals(StatusCode.ERROR, span.status.statusCode)

        // We can also check the recorded exception event if desired
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNotNull(exceptionEvent, "Should record an exception event")
    }

    /* -------------------------------------------------------
       Result-returning function tests
       ------------------------------------------------------- */

    @Test
    fun `test result succeeding operation is traced`() {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        val input = "TestResult"
        val result = sut.resultSucceedingOperation(input)

        assertTrue(result.isSuccess)
        assertEquals(input, result.getOrThrow())

        val spans = inMemoryExporter.getFinishedSpans()
        assertEquals(1, spans.size)
        val span = spans.first()
        // Should match annotation name = "result_succ_op"
        assertEquals("result_succ_op", span.name)
        assertEquals(StatusCode.OK, span.status.statusCode)

        // No exception event expected
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNull(exceptionEvent, "No exception event expected for success")

        // Now verify the captured parameter ("input") and additional context ("sample").
        // Adjust the key names/types if your instrumentation differs.
        val attributes = span.attributes

        // 1) Captured parameter
        val inputAttr = attributes.get(AttributeKey.stringKey("input"))
        assertEquals(input, inputAttr, "Should capture the function parameter 'input'")

        // 2) Additional context from property "sample" (which is Int? = 1 in your UserServiceImpl).
        // Depending on your instrumentation, this may be stored as a longKey or stringKey.
        val sampleAttr = attributes.get(AttributeKey.stringKey("sample"))
        assertEquals(sut.sample.toString(), sampleAttr.toString())
    }


    @Test
    fun `test result succeeding suspend operation is traced`() = runTest {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        val input = "TestSuspendResult"
        val result = sut.resultSucceedingSuspendOperation(input)

        assertTrue(result.isSuccess)
        assertEquals(input, result.getOrThrow())

        val spans = inMemoryExporter.getFinishedSpans()
        assertEquals(1, spans.size)
        val span = spans.first()
        // No custom annotation => fallback to method name
        assertEquals("resultSucceedingSuspendOperation", span.name)
        assertEquals(StatusCode.OK, span.status.statusCode)

        // No exception event expected
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNull(exceptionEvent, "No exception event expected for success")
    }

    @Test
    fun `test result failing operation`() {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        val exceptionMessage = "Result failure"
        val res = sut.resultFailingOperation(IllegalArgumentException(exceptionMessage))
        assertTrue(res.isFailure)
        assertFailsWith<IllegalArgumentException> { res.getOrThrow() }

        val spans = inMemoryExporter.getFinishedSpans()
        assertEquals(1, spans.size)
        val span = spans.first()
        // Should match the custom annotation name = "result_failed_op"
        assertEquals("result_failed_op", span.name)
        assertEquals(StatusCode.ERROR, span.status.statusCode)

        // Check exception event
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNotNull(exceptionEvent, "Should record an exception event for failure")
    }

    @Test
    fun `test result failing suspend operation`() = runTest {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        val exceptionMessage = "Result suspend failure"
        val result = sut.resultFailingSuspendOperation(IllegalArgumentException(exceptionMessage))

        assertTrue(result.isFailure)
        val exception = assertFailsWith<IllegalArgumentException> {
            result.getOrThrow()
        }
        assertEquals(exceptionMessage, exception.message)

        val spans = inMemoryExporter.getFinishedSpans()
        assertEquals(1, spans.size)
        val span = spans.first()
        // Should match annotation name = "result_failed_suspend_op"
        assertEquals("result_failed_suspend_op", span.name)
        assertEquals(StatusCode.ERROR, span.status.statusCode)

        // Check exception event
        val exceptionEvent = span.events.firstOrNull { it.name == "exception" }
        assertNotNull(exceptionEvent, "Should record an exception event for failure")
    }

    /* -------------------------------------------------------
       Nested interface tests
       ------------------------------------------------------- */

    @Test
    fun `test nested optional interface property passthrough works correctly`() {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        // The nestedOptional's sample property is 3
        assertEquals(3, sut.nestedOptional?.sample)

        // Mutate
        sut.nestedOptional?.mutating = 42
        assertEquals(42, sut.nestedOptional?.mutating)

        // Ensure no spans for property access
        val spans = inMemoryExporter.getFinishedSpans()
        assertTrue(spans.isEmpty(), "No spans should be generated for property access or mutation")
    }

    @Test
    fun `test nested optional interface function`() = runTest {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        // Call nested optional
        val result = sut.nestedOptional?.resultSucceedingSuspendOperation("NestedOK")
        assertTrue(result?.isSuccess == true)
        assertEquals("NestedOK", result?.getOrThrow())

        val spans = inMemoryExporter.getFinishedSpans()
        assertTrue(spans.isNotEmpty())
        val span = spans.first()
        // The nested method had @Traceable.Span(name="nested_result_successful_suspend_op")
        assertEquals("nested_result_successful_suspend_op", span.name)
        assertEquals(StatusCode.OK, span.status.statusCode)
    }

    @Test
    fun `test nested required interface property passthrough works correctly`() {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        // The nestedRequired's sample property is 3
        assertEquals(3, sut.nestedRequired.sample)

        // Mutate
        sut.nestedRequired.mutating = 42
        assertEquals(42, sut.nestedRequired.mutating)

        // Ensure no spans for property access
        val spans = inMemoryExporter.getFinishedSpans()
        assertTrue(spans.isEmpty(), "No spans should be generated for property access or mutation")
    }

    @Test
    fun `test nested required interface function works`() = runTest {
        val real = SomeServiceImpl(SomeServiceImpl.NestedImpl(), SomeServiceImpl.NestedImpl())
        val sut = real.traced(tracer)

        // Call nested required
        val input = "UserServiceImpl.NestedImplInput"
        val result = sut.nestedRequired.resultSucceedingSuspendOperation(input)

        assertTrue(result.isSuccess)
        assertEquals(input, result.getOrThrow())

        val spans = inMemoryExporter.getFinishedSpans()
        assertTrue(spans.isNotEmpty())
        val span = spans.first()
        // The nested method had @Traceable.Span(name="nested_result_successful_suspend_op")
        assertEquals("nested_result_successful_suspend_op", span.name)
        assertEquals(StatusCode.OK, span.status.statusCode)
    }
}

class InMemorySpanExporter : SpanExporter {
    private val finishedSpans = mutableListOf<SpanData>()

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        finishedSpans.addAll(spans)
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    /** Returns a copy of all finished spans so far. */
    fun getFinishedSpans(): List<SpanData> = finishedSpans.toList()

    /** Clears all previously exported spans. */
    fun reset() {
        finishedSpans.clear()
    }
}


/* ===========================================================
   3) Concrete implementation of UserService
   =========================================================== */
class SomeServiceImpl(
    override val nestedOptional: SomeService.Nested,
    override val nestedRequired: SomeService.Nested
) : SomeService {

    override var mutating: Int = 0
    override val sample: Int?
        get() = 1

    override fun getUser(id: String): String {
        return id
    }

    override fun validateCredentials(username: String, password: String): Result<Boolean> {
        return if (username == "admin" && password == "1234") {
            Result.success(true)
        } else {
            Result.success(false)
        }
    }

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

    /* ===========================================================
   4) Concrete implementation for the Nested interface
   =========================================================== */
    class NestedImpl : SomeService.Nested {
        override var mutating: Int = 0
        override val sample: Int?
            get() = 3

        override suspend fun resultSucceedingSuspendOperation(input: String): Result<String> {
            return Result.success(input)
        }
    }
}


