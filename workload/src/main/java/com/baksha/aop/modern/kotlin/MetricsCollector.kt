package com.baksha.aop.modern.kotlin

import monitorable.Monitor
import kotlin.time.Duration.Companion.milliseconds

// Custom collector that tracks performance metrics
class MetricsCollector : Monitor.Collector {
    private val metrics = mutableMapOf<String, MutableList<Long>>()
    private val errors = mutableMapOf<String, MutableList<Throwable>>()

    override fun collect(data: Monitor.Data) {
        metrics.getOrPut(data.key) { mutableListOf() }
            .add(data.durationMillis)

        data.exception?.let { error ->
            errors.getOrPut(data.key) { mutableListOf() }
                .add(error)
        }
    }

    fun printReport() {
        println("\nPerformance Report")
        println("=================")
        metrics.forEach { (key, durations) ->
            val avgDuration = durations.average().milliseconds
            val errorRate = (errors[key]?.size ?: 0) * 100.0 / durations.size

            println("""
                Operation: $key
                Average Duration: $avgDuration
                Error Rate: $errorRate%
                Total Calls: ${durations.size}
                Error Types: ${errors[key]?.groupBy { it::class.simpleName }?.mapValues { it.value.size } ?: "none"}
                """.trimIndent())
            println("---")
        }
    }
}