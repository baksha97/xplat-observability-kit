package com.baksha.observability.app

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.lang.Exception

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

//fun main() = runBlocking {
//    System.setProperty("otel.log.level", "DEBUG")
//    RandomSpanGenerator.complexSpanTestRandom()
//    delay(1_000_000)
//}

fun main(): Unit = runBlocking {
    val userService = UserServiceImpl(TestNested(), TestNested())
        .traced(SampleApp.tracer)
    userService.resultFailingSuspendOperation(Exception("Error, World!"))
    delay(1000)
}