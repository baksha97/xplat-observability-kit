package com.baksha.observability.core

import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

/**
 * Provides functionality for capturing method execution metrics such as duration and exceptions.
 * This interface is typically implemented by monitoring proxies to add automatic performance
 * and error tracking capabilities to existing classes.
 *
 * The interface provides utility methods to wrap method executions with timing and error tracking,
 * delegating the actual collection of metrics to a [Monitor.Collector] implementation.
 *
 * This generic centralization of utility functions significantly reduces the amount of generated code required for
 * a given proxy.
 *
 * We're leveraging an abstract class since interfaces do not allow for inline functions since it's a virtual member.
 * By using inline functions:
 * - Avoid having to explicitly deal with suspend functions
 * - Limit impact on the call stack since it's called in place
 */
public abstract class Capturing(
    /**
     * The collector responsible for handling captured metrics.
     * Implementations will receive timing information and any exceptions that occur
     * during method execution.
     */
    public val collector: Monitor.Collector
) {

    /**
     * Executes a throwing operation while capturing its execution metrics.
     * If the operation throws an exception, it will be captured and then rethrown.
     *
     * @param key A string identifier for the operation being monitored
     * @param closure The operation to execute and monitor
     * @return The result of the operation
     * @param E The type of value returned by the operation
     * @throws Throwable if the closure throws an exception
     */
    public inline fun <E> withThrowingCapture(
        key: String,
        closure: () -> E
    ): E =
        capture(key) { runCatching { closure() } }
            .value
            .getOrThrow()

    /**
     * Executes an operation that returns a [Result] while capturing its execution metrics.
     * This method is particularly useful for operations that already handle their own exceptions
     * using Kotlin's [Result] type.
     *
     * @param key A string identifier for the operation being monitored
     * @param closure The operation to execute and monitor, which returns a [Result]
     * @return The [Result] returned by the operation
     * @param E The type of value wrapped in the [Result]
     */
    public inline fun <E> withResultCapture(
        key: String,
        closure: () -> Result<E>
    ): Result<E> =
        capture(key) { closure() }
            .value

    /**
     * Internal helper function that performs the actual metric capture.
     * This function measures the execution time of the provided closure and
     * delegates the captured metrics to the collector.
     *
     * @param key A string identifier for the operation being monitored
     * @param closure The operation to execute and monitor
     * @return A [TimedValue] containing both the result and execution duration
     * @param T The type of value wrapped in the [Result] returned by the closure
     */
    public inline fun <T> capture(
        key: String,
        closure: () -> Result<T>
    ): TimedValue<Result<T>> {
        val measured = measureTimedValue {
            closure()
        }
        collector.collect(
            Monitor.Data(
                key = key,
                durationMillis = measured.duration.inWholeMilliseconds,
                exception = measured.value.exceptionOrNull()
            )
        )
        return measured
    }
}