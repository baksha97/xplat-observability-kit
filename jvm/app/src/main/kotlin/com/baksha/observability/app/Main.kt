package com.baksha.observability.app

import com.baksha.observability.core.trace.SpanCapturing
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

val tracer: Tracer = run {
    println("Creating OTEL Tracer..")
    val resource = Resource
        .builder()
        .put(ResourceAttributes.SERVICE_NAME, "java-cli-app")
        .build()
    val spansIngestUrl = "http://localhost:4318/v1/traces"
    val logsIngestUrl = "http://localhost:4318/v1/logs"
    // Set up Jaeger exporter
    val jaegerExporter = OtlpHttpSpanExporter
        .builder()
        .setEndpoint(spansIngestUrl)
        .build()

    // Set up the tracer provider with the Jaeger exporter
    val tracerProvider = SdkTracerProvider
        .builder()
        .setResource(resource)
        .addSpanProcessor(SimpleSpanProcessor.create(jaegerExporter))
        .addSpanProcessor(SimpleSpanProcessor.create(ConsoleSpanExporter()))
        .build()

    // Set up OpenTelemetry
    val openTelemetry = OpenTelemetrySdk
        .builder()
        .setTracerProvider(tracerProvider)
        .buildAndRegisterGlobal()
    openTelemetry.tracerBuilder("com").build()
}

object DefaultCapturing : SpanCapturing(tracer)

fun debugUserService(): Unit = runBlocking {
    val userService = UserServiceImpl(TestNested(), TestNested())
        .traced(tracer)
    userService.resultFailingSuspendOperation(Exception("Error, World!"))
    userService.getUser("test")
    delay(1000)
}

fun generateRandomSpans() = runBlocking {
    val generator = RandomSpanGenerator(tracer)
    generator.complexSpanTestRandom()
    delay(30.seconds)
}

suspend fun drawManualSpans(capture: SpanCapturing) = with(capture) {
    withSuspendingSpanCapture("suspend1") {
        withSuspendingSpanCapture("suspend1.suspend1") { }
        withSpanCapture("suspend1.sync1") { }
        withSpanCapture("suspend1.sync2") {
            withSpanCapture("suspend1.sync.sync1") { }
            withSpanCapture("suspend1.sync.sync2") { }
            withSpanCapture("suspend1.sync.sync3") { }
        }
        withSpanCapture("suspend1.sync3") {
            withSpanCapture("suspend1.sync3.sync1") { }
            withSpanCapture("suspend1.sync3.sync2") { }
        }

        withSuspendingSpanCapture("suspend1.suspend2") {
            withSuspendingSpanCapture("suspend1.suspend2.suspend1") { }
            withSuspendingSpanCapture("suspend1.suspend2.suspend2") { }
        }
    }
}

suspend fun drawManualSpansNested(capture: SpanCapturing) = with(capture) {
    drawManualSpans(capture)
    drawManualSpans(capture)
    drawManualSpans(capture)
    withSuspendingSpanCapture("suspend1") {
        withSuspendingSpanCapture("suspend1.suspend1") {
            drawManualSpans(capture)
        }
        withSuspendingSpanCapture("suspend1.suspend2") {
            withSuspendingSpanCapture("suspend1.suspend2.suspend1") {
                drawManualSpans(capture)
            }
            withSuspendingSpanCapture("suspend1.suspend2.suspend2") {
                drawManualSpans(capture)
            }
            drawManualSpans(capture)
        }
    }
}


fun main() = runBlocking {
    drawManualSpans(DefaultCapturing)
//    val generator = RandomSpanGenerator(tracer)
//    generator.complexSpanTestRandom()
    delay(30.seconds)
}



