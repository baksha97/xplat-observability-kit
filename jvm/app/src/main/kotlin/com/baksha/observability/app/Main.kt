package com.baksha.observability.app

import com.baksha.observability.core.span.SpanCapturing
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
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

//        val spansIngestUrl = "http://10.0.2.2:4318/v1/traces"
//        val logsIngestUrl = "http://10.0.2.2:4318/v1/logs"
        val spansIngestUrl = "http://localhost:4318/v1/traces"
        val logsIngestUrl = "http://localhost:4318/v1/logs"
        // Set up Jaeger exporter
        val jaegerExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(spansIngestUrl)
            .build()

        // Set up the tracer provider with the Jaeger exporter
        val tracerProvider = SdkTracerProvider
            .builder()
            .addSpanProcessor(BatchSpanProcessor.builder(jaegerExporter).build())
            .addSpanProcessor(BatchSpanProcessor.builder(ConsoleSpanExporter()).build())
            .build()

        // Set up OpenTelemetry
        val openTelemetry = OpenTelemetrySdk
            .builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal()
//        tracer = openTelemetry.tracerProvider.get("sample-app")
        tracer = openTelemetry.tracerBuilder("sample=scope").build()
    }
}

object ExampleSystem : SpanCapturing(SampleApp.tracer) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun doSomething() {
        println(":doSomething:...")

        withSpanCapture("do_something1") {
            println("Doing 1")
            withSpanCapture("do_something2") {
                println("Doing 2")

            }
        }
    }

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

    fun demoLaunchContextLoss() = withSpanCapture("demoLaunchContextLoss") {
        scope.launch {
            withSpanCapture("launch1-sync") {
                it.addEvent("launch1-event")
                launch {
                    withSpanCapture("launch1-wsync") {
                        withSpanCapture("launch1-wsync-wsync") {}
                    }
                    withSuspendingSpanCapture("launch1-wsuspend") {
                        withSuspendingSpanCapture("launch1-wsuspend-wsuspend") {}
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
}

fun main() = runBlocking {
    System.setProperty("otel.log.level", "DEBUG")
    ExampleSystem.demoLaunchContextLoss()
//    ExampleSystem.doSomething()
    delay(1_000_000)
}