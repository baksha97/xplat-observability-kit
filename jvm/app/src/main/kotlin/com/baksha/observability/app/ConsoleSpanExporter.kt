package com.baksha.observability.app

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

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