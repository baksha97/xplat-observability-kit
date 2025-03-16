package com.baksha.observability.app

import com.baksha.observability.core.span.SpanCapturing
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

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
    private val scope = CoroutineScope(Dispatchers.IO)

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

            // TEST: Does manually propagate it the way I expect it to work, work?....
            // This works too... Looks like launch doesn't propagate the context.
            scope.launch(coroutineContext) {
                withSpanCapture("withSpanCapture.scope.launch+coroutineContext") {}
                withSuspendingSpanCapture("withSuspendingSpanCapture.scope.launch+coroutineContext") {}
            }
        }

    suspend fun complexSpanTestAll(): Unit =
        withSuspendingSpanCapture("1. Outer Suspended Span") {
            // Branch A: Sibling non-suspending spans (using withSpanCapture and withSpanCaptureResult)
            // They run in a non-suspending context and do not call any suspend functions.
            runCatching {
                withSpanCapture("A.1 NonSuspending Span 1") {
                    // Non-suspending work.
                }
                withSpanCaptureResult<Unit>("A.2 NonSuspending Span Result 2") {
                    // Simulate an error.
                    throw Exception("Error in non-suspending span result 2")
                }
            }

            // Branch B: Nested suspending spans using withSuspendingSpanCapture and withSuspendingSpanCaptureResult
            // All nested calls here are suspending, so deeper layers can be built.
            runCatching {
                withSuspendingSpanCapture("B.1 Suspending Span Level 1") {
                    withSuspendingSpanCaptureResult<Unit>("B.2 Suspending Span Result Level 2") {
                        withSuspendingSpanCapture("B.3 Suspending Span Level 3") {
                            runCatching {  }
                        }
                    }
                }
            }

            // Branch C: Within a suspending span, add siblings that are non-suspending.
            runCatching {
                withSuspendingSpanCapture<Unit>("C.1 Suspending Span Level 1") {
                    // Sibling non-suspending span.
                    withSpanCapture("C.1.1 NonSuspending Sibling Span") {
                        // Do some non-suspending work.
                    }
                    // Another non-suspending sibling that throws an error.
                    withSpanCaptureResult<String>("C.1.2 NonSuspending Result Sibling Span") {
                        runCatching { throw Exception("Error in NonSuspending Result Sibling Span") }
                    }
                }
            }

            // Branch D: Mixed siblings in a suspending span-result block.
            // Here, we first use a non-suspending sibling, then a suspending one.
            runCatching<Unit>{
                withSuspendingSpanCaptureResult<Unit>("D.1 Suspending Span Result Level 1") {
                    // Non-suspending sibling.
                    withSpanCapture("D.2 NonSuspending Sibling") {
                        // Non-suspending work.
                    }
                    withSpanCaptureResult<String>("D.2.5 NonSuspending Result Sibling Span") {
                        runCatching { throw Exception("Error in NonSuspending Result Sibling Span") }
                    }
                    // Suspending sibling.
                    withSuspendingSpanCapture("D.3 Suspending Sibling") {
                        // Within this suspending block, add a non-suspending sibling.
                        withSpanCapture("D.3.1 NonSuspending Sibling inside Suspended Block") {
                            // Non-suspending work.
                        }
                        withSpanCaptureResult<String>("D.3.5 NonSuspending Result Sibling Span") {
                            runCatching { throw Exception("Error in NonSuspending Result Sibling Span") }
                        }
                        // And then a deeper suspending span that throws an error.
                        withSuspendingSpanCapture("D.3.2 Nested Suspended Span") {
                            runCatching {  }
//                            throw Exception("Error in Nested Suspended Span in D.3.2")
                        }
                    }
                }
            }

            // Branch E: 10-level deep chain of suspending spans (using only suspending functions)
            runCatching {
                withSuspendingSpanCapture<Unit>("E.1 Level 1") {
                    withSuspendingSpanCapture("E.2 Level 2") {
                        withSuspendingSpanCapture("E.3 Level 3") {
                            withSuspendingSpanCapture("E.4 Level 4") {
                                withSuspendingSpanCapture("E.5 Level 5") {
                                    withSuspendingSpanCapture("E.6 Level 6") {
                                        withSuspendingSpanCapture("E.7 Level 7") {
                                            withSuspendingSpanCapture("E.8 Level 8") {
                                                withSuspendingSpanCapture("E.9 Level 9") {
                                                    withSuspendingSpanCapture("E.10 Level 10") {
                                                        Unit
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
            }

            // Branch F: 10-level deep chain of non-suspending spans using withSpanCapture and withSpanCaptureResult.
            // Since these are non-suspending blocks, they only call non-suspending functions.
            runCatching {
                withSpanCapture<Unit>("F.1 NonSuspending Level 1") {
                    withSpanCaptureResult("F.2 NonSuspending Level 2") {
                        withSpanCapture("F.3 NonSuspending Level 3") {
                            withSpanCaptureResult("F.4 NonSuspending Level 4") {
                                withSpanCapture("F.5 NonSuspending Level 5") {
                                    withSpanCaptureResult("F.6 NonSuspending Level 6") {
                                        withSpanCapture("F.7 NonSuspending Level 7") {
                                            withSpanCaptureResult("F.8 NonSuspending Level 8") {
                                                withSpanCapture("F.9 NonSuspending Level 9") {
                                                    withSpanCaptureResult("F.10 NonSuspending Level 10") {
                                                        runCatching { }
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
            }
        }
}

fun main() = runBlocking {
    System.setProperty("otel.log.level", "DEBUG")
    ExampleSystem.demoLaunchContextLoss()
    delay(1_000_000)
}