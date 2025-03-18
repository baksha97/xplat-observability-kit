package com.baksha.observability.core.collect

/**
 * Interface for collecting and processing monitoring data.
 * Implementations can define custom handling of monitoring events,
 * such as logging, metrics collection, or alerting.
 */
public fun interface Collector {
    /**
     * Processes a monitoring event.
     *
     * @param data The monitoring data to process
     */
    public fun collect(data: Data)

    /**
     * Represents a single monitoring event with execution metrics.
     *
     * @property key The identifier for the monitored operation
     * @property durationMillis The execution time in milliseconds
     * @property exception Any exception that occurred during execution, or null if successful
     */
    public data class Data(
        val key: String,
        val durationMillis: Long,
        val exception: Throwable? = null,
    )

    companion object {
        /**
         * A simple collector that prints monitoring data to standard output.
         * Useful for debugging and development purposes.
         *
         * Example usage:
         * ```
         * val service = userService.monitored(Collector.console())
         * ```
         */
        fun console(): Collector = Collector(::println)

        /**
         * A collector that delegates to multiple other collectors.
         * Useful for sending monitoring data to multiple destinations simultaneously.
         *
         * @property collectors The collectors to delegate to.
         *
         * Example usage (leveraging the built-in varargs):
         * ```
         * val service = userService.monitored(
         *    Collector.console(),
         *    myCustomMetricsCollector
         * )
         * ```
         */
        fun composite(vararg collectors: Collector): Collector =
            Collector { data ->
                collectors.forEach { it.collect(data) }
            }
    }
}