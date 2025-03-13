package com.baksha.observability.core

/**
 * Core monitoring functionality that provides annotations and data structures for method execution tracking.
 * This object serves as the main entry point for the monitoring system and public API containing annotations to mark
 * monitorable elements and classes to collect and process monitoring data.
 */
public object Monitor {
    /**
     * Marks an interface as eligible for monitoring code generation.
     * When applied to an interface, the annotation processor will generate a monitoring proxy
     * that implements the interface and captures execution metrics for all methods.
     *
     * Example usage:
     * ```
     * @Monitor.Collectable
     * interface UserService {
     *     fun getUser(id: String): User
     * }
     * ```
     */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    public annotation class Collectable

    /**
     * Customizes the monitoring key for a specific function.
     * When applied to a function in a [Collectable] interface, this annotation allows
     * specifying a custom name for the monitoring data instead of using the method name.
     *
     * @property name The custom name to use for monitoring this function
     *
     * Example usage:
     * ```
     * @Monitor.Function("user.fetch")
     * fun getUser(id: String): User
     * ```
     */
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    public annotation class Function(val name: String)

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
    }

    /**
     * Provides built-in implementations of [Collector].
     */
    public object Collectors {
        /**
         * A simple collector that prints monitoring data to standard output.
         * Useful for debugging and development purposes.
         *
         * Example usage:
         * ```
         * val service = userService.monitored(Monitor.Collectors.Printer())
         * ```
         */
        public class Printer: Collector {
            override fun collect(data: Data) {
                println(data)
            }
        }

        /**
         * A collector that delegates to multiple other collectors.
         * Useful for sending monitoring data to multiple destinations simultaneously.
         *
         * @property collectors The collectors to delegate to.
         *
         * Example usage (leveraging the built-in varargs):
         * ```
         * val service = userService.monitored(
         *    Monitor.Collectors.Printer(),
         *    myCustomMetricsCollector
         * )
         * ```
         */
        public class Composite(private vararg var collectors: Collector) : Collector {
            override fun collect(data: Data) {
                collectors.forEach { it.collect(data) }
            }
        }
    }
}