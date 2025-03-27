package com.baksha.observability.core

interface Driver {
    val id: String
    fun install(): Backend
}

/**
 * We need to create a backend for
 * 1) Console Printing Backend
 * 2) Noop Backend
 * 3) Composite Backend
 */
interface Backend1 {
    val configuration: Configuration

    val tracer: Tracer?
    val logger: Logger?
    val metrics: Metrics?
    val rum: Rum?

    interface Tracer {
        fun trace()
    }
    interface Logger {
        fun log()
    }
    interface Metrics {
        fun measure()
    }
    interface Rum {
        fun monitor()
    }

    data class Configuration(
        val level: String
    )
}

object ObservabilityKit1 {
    fun bootstrap(driver: Driver): Backend {
        return driver.install()
    }

    private val backend: Backend? = null
}


// Configuration with more flexible options
data class ObservabilityConfiguration(
    val level: Level = Level.INFO,
    val tags: Map<String, String> = emptyMap()
) {
    enum class Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
}

// Abstract Backend with Fun Interfaces
interface Backend {
    val configuration: ObservabilityConfiguration
    val tracer: Tracer
    val logger: Logger
    val metrics: Metrics
    val rum: Rum

    fun interface Tracer {
        fun trace(message: String)
    }

    fun interface Logger {
        fun log(message: String)
    }

    fun interface Metrics {
        fun measure(value: Number)
    }

    fun interface Rum {
        fun monitor(event: String)
    }
}

// Concrete Backend Implementations
class ConsoleBackend(
    override val configuration: ObservabilityConfiguration = ObservabilityConfiguration()
) : Backend {
    override val tracer = Backend.Tracer { message ->
        println("TRACE: $message")
    }

    override val logger = Backend.Logger { message ->
        println("LOG: $message")
    }

    override val metrics = Backend.Metrics { value ->
        println("METRICS: ${value}")
    }

    override val rum = Backend.Rum { event ->
        println("RUM: $event")
    }
}

class NoopBackend(
    override val configuration: ObservabilityConfiguration = ObservabilityConfiguration()
) : Backend {
    override val tracer = Backend.Tracer { /* No-op */ }
    override val logger = Backend.Logger { /* No-op */ }
    override val metrics = Backend.Metrics { /* No-op */ }
    override val rum = Backend.Rum { /* No-op */ }
}

class MemoryCacheBackend(
    override val configuration: ObservabilityConfiguration = ObservabilityConfiguration()
) : Backend {
    private val tracedEvents = mutableListOf<String>()
    private val loggedEvents = mutableListOf<String>()
    private val measuredEvents = mutableListOf<Number>()
    private val monitoredEvents = mutableListOf<String>()

    override val tracer = Backend.Tracer { message ->
        tracedEvents.add("Trace: $message at ${System.currentTimeMillis()}")
    }

    override val logger = Backend.Logger { message ->
        loggedEvents.add("Log: $message at ${System.currentTimeMillis()}")
    }

    override val metrics = Backend.Metrics { value ->
        value?.let { measuredEvents.add(it) }
    }

    override val rum = Backend.Rum { event ->
        monitoredEvents.add("Monitor: $event at ${System.currentTimeMillis()}")
    }

    fun replayEvents() {
        println("Traced Events: $tracedEvents")
        println("Logged Events: $loggedEvents")
        println("Measured Events: $measuredEvents")
        println("Monitored Events: $monitoredEvents")
    }
}

object ObservabilityKit {
    @Volatile
    private var isBootstrappedInternal = false

    private var backendInitializer: (() -> Backend)? = null

    // Lazy delegate that can be reset
    private var lazyBackend: Lazy<Backend>? = null

    fun bootstrap(driver: Driver): Backend {
        // Synchronized check to prevent multiple bootstrapping
        synchronized(this) {
            check(!isBootstrappedInternal) {
                "ObservabilityKit has already been bootstrapped."
            }

            backendInitializer = { driver.install() }
            lazyBackend = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
                check(isBootstrappedInternal) {
                    "ObservabilityKit must be bootstrapped before accessing backend"
                }
                backendInitializer!!()
            }
            isBootstrappedInternal = true
        }

        // Trigger lazy initialization
        return get()
    }

    // Public accessor that ensures initialization
    fun get(): Backend {
        return lazyBackend?.value
            ?: throw IllegalStateException("ObservabilityKit has not been bootstrapped")
    }

    // Check if bootstrapped
    val isBootstrapped: Boolean
        get() = isBootstrappedInternal

    // Convenience methods
    fun trace(message: String = "") = get().tracer.trace(message)
    fun log(message: String = "") = get().logger.log(message)
    fun measure(value: Number? = null) = value?.let { get().metrics.measure(it) }
    fun monitor(event: String = "") = get().rum.monitor(event)

    // Reset method for testing
    internal fun reset() {
        synchronized(this) {
            isBootstrappedInternal = false
            backendInitializer = null
            lazyBackend = null
        }
    }
}