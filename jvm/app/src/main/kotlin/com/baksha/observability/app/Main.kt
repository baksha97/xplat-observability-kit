package com.baksha.observability.app

import com.baksha.observability.core.span.SpanCapturing
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ConsoleSpanExporter: SpanExporter {
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

object ExampleSystem: SpanCapturing(SampleApp.tracer) {
    fun doSomething() {
        println(":doSomething:...")

        withSpanCapture("do_something1") {
            println("Doing 1")
            withSpanCapture("do_something2") {
                println("Doing 2")
            }
        }
    }

}

fun main() = runBlocking {
    System.setProperty("otel.log.level", "DEBUG")
    ExampleSystem.doSomething()
    delay(1_000_000_000)
}