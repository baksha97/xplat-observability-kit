package com.baksha.observability.app

import com.baksha.observability.core.span.SpanCapturing
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ConsoleSpanExporter : SpanExporter {
    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        spans.forEach {
            println("====${it.name}====")
            println(it)
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

}

object SampleApp {
    val tracer: Tracer

    init {
        println("Initializing OpenTelemetry SDK...")
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
//            .addSpanProcessor(BatchSpanProcessor.builder(jaegerExporter).build())
//            .addSpanProcessor(BatchSpanProcessor.builder(ConsoleSpanExporter()).build())
            .build()

        // Set up OpenTelemetry
        val openTelemetry = OpenTelemetrySdk
            .builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal()

        tracer = openTelemetry.tracerBuilder("com").build()
    }
}

object ExampleSystem : SpanCapturing(SampleApp.tracer) {
    // Recursively generate spans.
// currentLevel: current depth in the span tree.
// maxLevel: maximum allowed depth to prevent infinite recursion.
// labelPrefix: a label prefix that gets extended at each level.
    private suspend fun generateRandomSpans(currentLevel: Int, maxLevel: Int, labelPrefix: String) {
        if (currentLevel > maxLevel) return

        // Randomly choose between suspending and non-suspending span
        val isSuspending = Random.nextBoolean()

        // Generate a label for this span based on current level and prefix.
        val spanLabel = "$labelPrefix (Level $currentLevel)"

        // Randomly decide to simulate an error in this span
        val shouldError = false //Random.nextInt(10) < 2  // ~20% chance

        // Choose one of the span-capturing functions based on the type
        if (isSuspending) {
            if (Random.nextBoolean()) {
                withSuspendingSpanCapture(spanLabel) {
                    // Simulate work and possible error
                    if (shouldError) throw Exception("Simulated error in suspending span: $spanLabel")
                    // Generate a random number of child spans
                    val childCount = Random.nextInt(0, 6)
                    repeat(childCount) { i ->
                        // Use a letter or number for the child label (e.g., "A", "B", "C")
                        val childLabel = "$spanLabel.${('A' + i)}"
                        // Randomly decide between result-capturing or plain span capture for the child
                        if (Random.nextBoolean()) {
                            withSuspendingSpanCapture(childLabel) {
                                withContext(Dispatchers.Unconfined) {
                                    generateRandomSpans(currentLevel + 1, maxLevel, childLabel)
                                }
                            }
                        } else {
                            withContext(Dispatchers.IO) {
                                generateRandomSpans(currentLevel + 1, maxLevel, childLabel)
                            }                        }
                    }
                }
            } else {
                withSuspendingSpanCapture(spanLabel) {
                    if (shouldError) throw Exception("Simulated error in suspending result span: $spanLabel")
                    val childCount = Random.nextInt(0, 6)
                    repeat(childCount) { i ->
                        val childLabel = "$spanLabel.${('A' + i)}"
                        withContext(Dispatchers.IO) {
                            generateRandomSpans(currentLevel + 1, maxLevel, childLabel)
                        }                    }
                }
            }
        } else {
            if (Random.nextBoolean()) {
                withSuspendingSpanCapture(spanLabel) {
                    if (shouldError) throw Exception("Simulated error in non-suspending span: $spanLabel")
                    val childCount = Random.nextInt(0, 6)
                    repeat(childCount) { i ->
                        val childLabel = "$spanLabel.${('A' + i)}"
                        withContext(Dispatchers.IO) {
                            generateRandomSpans(currentLevel + 1, maxLevel, childLabel)
                        }                    }
                }
            } else {
                withSuspendingSpanCapture(spanLabel) {
                    if (shouldError) throw Exception("Simulated error in non-suspending result span: $spanLabel")
                    val childCount = Random.nextInt(0, 6)
                    repeat(childCount) { i ->
                        val childLabel = "$spanLabel.${('A' + i)}"
                        withContext(Dispatchers.Default) {
                            generateRandomSpans(currentLevel + 1, maxLevel, childLabel)
                        }                    }
                }
            }
        }
    }

    // Top-level function to start generating spans.
    suspend fun complexSpanTestRandom() {
        // Start with an outer suspending span.
        withSuspendingSpanCapture("1. Outer Suspended Span") {
            // For example, generate spans up to a maximum depth of 5.
            generateRandomSpans(currentLevel = 1, maxLevel = 3, labelPrefix = "Root")
        }
    }
}

fun main() = runBlocking {
    System.setProperty("otel.log.level", "DEBUG")
    ExampleSystem.complexSpanTestRandom()
    delay(1_000_000)
}