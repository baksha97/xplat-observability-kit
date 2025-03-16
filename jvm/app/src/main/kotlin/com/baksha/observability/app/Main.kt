package com.baksha.observability.app

import com.baksha.observability.core.span.SpanCapturing
import com.baksha.observability.core.span.withSuspendingSpan
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.*


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
            .addSpanProcessor(BatchSpanProcessor.builder(jaegerExporter).build())
            .addSpanProcessor(BatchSpanProcessor.builder(ConsoleSpanExporter()).build())
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
    private val scope = CoroutineScope(Dispatchers.IO)

    fun doSuspending() {
        with(scope) {
            launch {
                withSuspendingSpanCapture("root") {
                    withSpanCapture("root-sub") { }
                    launch {
                        withSuspendingSpanCapture("coroutine1-start") {
                            withSuspendingSpanCapture("coroutine1-sub") {
                                withSpanCapture("coroutine1-sub-1") {

                                }
                                withSuspendingSpanCapture("coroutine1-sub-2") {

                                }
                            }
                            launch {
                                withSuspendingSpanCapture("coroutine2-start") {
                                    withSuspendingSpanCapture("coroutine2-sub") {
                                        withSuspendingSpanCapture("coroutine2-sub-sub") {

                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun demoLaunchContextLoss() = withSpanCapture("demoLaunchContextLoss") { rootSpan ->
        scope.launch {
            withSpanCapture("launch1-sync") {
                it.addEvent("launch1-event")
                launch() {
                    withSpanCapture("launch1-wsync") {
                        withSpanCapture("launch1-wsync-wsync") {}
                        withSpanCapture("launch1-wsync-wsync-dispatcher") {}
                    }
                    withSuspendingSpanCapture("launch1-wsuspend") {
                        withSpanCapture("launch1-wsuspend-wsync") {}
                        withSuspendingSpanCapture("launch1-wsuspend-wsuspend") {}
                        withContext(Dispatchers.IO) {
                            withSuspendingSpanCapture("launch1-wsuspend-wsuspend-Dispatchers.IO") {}
                        }
                    }
                }
            }

            withSuspendingSpanCapture("launch2-suspend") {
                launch {
                    withSuspendingSpanCapture("inside2") {
                        withSpanCapture("launch2-wsync") {
                            withSpanCapture("launch2-wsync-wsync") {}
                        }
                        withSuspendingSpanCapture("launch2-wsuspend") {
                            withSpanCapture("launch2-wsuspend-wsync") {}
                            withSuspendingSpanCapture("launch2-wsuspend-wsuspend") {}
                        }
                    }
                }
            }
        }
    }

    suspend fun moreDebugging(): Unit =
        withSuspendingSpanCapture("withSuspendingSpanCapture") { rootContext ->
            // Correct
            withSpanCapture("withSpanCapture") {}
            // Correct
            withSuspendingSpanCapture("withSuspendingSpanCapture") {}

            // Launch but the span context is lost and gone
            scope.launch {
                // Created its own root context
                withSpanCapture("withSpanCapture.scope.launch") {}
                // Created its own root context
                withSuspendingSpanCapture("withSuspendingSpanCapture.scope.launch") {}
            }

            // To fix this, we need to pass the root context explicitly...
            // The span context is lost in the launch block for some reason....
            scope.launch(rootContext.asContextElement()) {
                withSpanCapture("withSpanCapture.scope.launch+rootContext") {}
                withSuspendingSpanCapture("withSuspendingSpanCapture.scope.launch+rootContext") {}
            }
        }
}

fun main() = runBlocking {
    System.setProperty("otel.log.level", "DEBUG")
    ExampleSystem.moreDebugging()
//    ExampleSystem.doSomething()
    delay(1_000_000)
}