package com.baksha.observability.app

import com.baksha.observability.core.trace.SpanCapturing
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class RandomSpanGenerator(tracer: Tracer) : SpanCapturing(tracer) {
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
                            }
                        }
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
                        }
                    }
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
                        }
                    }
                }
            } else {
                withSuspendingSpanCapture(spanLabel) {
                    if (shouldError) throw Exception("Simulated error in non-suspending result span: $spanLabel")
                    val childCount = Random.nextInt(0, 6)
                    repeat(childCount) { i ->
                        val childLabel = "$spanLabel.${('A' + i)}"
                        withContext(Dispatchers.Default) {
                            generateRandomSpans(currentLevel + 1, maxLevel, childLabel)
                        }
                    }
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