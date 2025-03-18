# Monitorable – Functional Method Monitoring & Tracing for Kotlin

A lightweight, zero-reflection approach to method monitoring **and tracing** in Kotlin using KSP (Kotlin Symbol Processing).

## Overview

Monitorable provides simple but powerful **monitoring** and **tracing** through compile-time code generation, featuring:
- Zero runtime reflection
- Compile-time generation
- Built-in error handling
- Composable collectors
- Minimal runtime overhead

In addition to monitoring via `@Monitor`, you can optionally add **OpenTelemetry-based tracing** via `@Traceable`, with **no** additional runtime reflection or overhead.

## Usage (Monitoring)

1. **Add the Monitor annotation** to your interface:
   ```kotlin
   @Monitor.Collectable
   interface AuthService {
       @Monitor.Function(name = "auth_user_get")
       fun getUser(): String
       
       @Monitor.Function(name = "auth_result_get")
       fun getResult(): Result<String>
   }
   ```

2. **Use the generated monitored extension**:
   ```kotlin
   // Single collector
   val service = authService.monitored(Monitor.Collectors.Printer())
   
   // Multiple collectors
   val service = authService.monitored(
       Monitor.Collectors.Printer(),
       MetricsCollector()
   )
   ```

## Usage (Tracing)

In parallel to—or instead of—monitoring, you can enable **tracing** for your interface using the `@Traceable` annotation. This generates OpenTelemetry-based **spans** around each method call:

1. **Annotate your interface with `@Traceable`**:
   ```kotlin
   @Traceable
   interface UserRepository {
       // For a default span name matching the method
       fun findById(id: String): User
       
       // Customize the span name, capture parameters, etc.
       @Traceable.Span(name = "create-user", captureParameters = ["username"])
       fun createUser(username: String, email: String): User
       
       // Skip tracing for certain methods entirely
       @Traceable.Ignore
       fun localCacheRefresh()
   }
   ```

2. **Use the generated `traced` extension** by providing a Tracer:
   ```kotlin
   import io.opentelemetry.api.trace.Tracer

   val tracer: Tracer = // Obtain or build an OpenTelemetry tracer
   val userRepo = actualUserRepo.traced(tracer)

   // Calls to userRepo methods now automatically create and manage spans
   val user = userRepo.findById("123")
   ```

### How `@Traceable` Works

- **`@Traceable` at the interface level**: Tells the KSP processor to generate a “spanning proxy.”
- **`@Traceable.Span` at the method level**: Customizes the span’s name and captures attributes like method parameters or additional fields.
- **`@Traceable.Ignore`**: Explicitly **skips** span capture for the method, delegating calls directly to the underlying implementation.

## Features

### Built-in Error Handling

Monitorable (and `@Traceable`) handles both regular exceptions and `Result`-wrapped returns:
```kotlin
@Monitor.Collectable
@Traceable
interface UserService {
    // Regular methods - exceptions are caught and reported or traced
    @Monitor.Function(name = "get_user")
    fun getUser(id: String): String
    
    // Result-returning methods - failures are tracked or traced
    @Traceable.Span(name = "validate-user")
    @Monitor.Function(name = "validate_user")
    fun validateUser(user: User): Result<Boolean>
}
```

### Simple Collector Interface (Monitoring)

Create custom collectors with a simple functional interface:
```kotlin
class MetricsCollector : Monitor.Collector {
    override fun collect(data: Monitor.Data) {
        // data.key - monitored method name
        // data.durationMillis - execution time
        // data.exception - any thrown exception
    }
}
```

### Tracing Data with OpenTelemetry

When using `@Traceable`, each method call can:
- Create a new span
- Optionally capture parameters in the span as attributes
- Propagate exceptions as “error” events
- Wrap suspend functions and `Result` types seamlessly

```kotlin
@Traceable
interface SomeService {
    @Traceable.Span(name = "some-operation", captureParameters = ["id"])
    suspend fun someOperation(id: String): Result<String>

    // Will not produce a span (simply delegates):
    @Traceable.Ignore
    fun debugLocalStuff()
}
```

### Built-in Collectors

Use the built-in collectors for monitoring, or combine them with your own:
```kotlin
// Simple printing collector (monitoring only)
val printer = Monitor.Collectors.Printer()

// Combine multiple collectors
val composite = Monitor.Collectors.Composite(
    Monitor.Collectors.Printer(),
    // Additional custom collectors...
)
```

For tracing, you’ll rely on standard OpenTelemetry backends and exporters, e.g., the `InMemorySpanExporter` for testing or Jaeger/Zipkin exporters in production.

## Complete Example

```kotlin
@Monitor.Collectable
@Traceable
interface UserService {
    // Monitored with a custom name, traced with a default name
    @Monitor.Function(name = "get_user")
    fun getUser(id: String): String

    // Traced with a custom name, also monitored
    @Traceable.Span(name = "validate-user")
    @Monitor.Function(name = "validate")
    fun validate(token: String): Result<Boolean>
    
    // Ignored by tracing, but still monitored
    @Traceable.Ignore
    @Monitor.Function(name = "sync_local")
    fun syncLocalCache(): Boolean
}

class MetricsCollector : Monitor.Collector {
    private val metrics = mutableMapOf<String, MutableList<Long>>()
    
    override fun collect(data: Monitor.Data) {
        metrics.getOrPut(data.key) { mutableListOf() }
            .add(data.durationMillis)
    }
    
    fun printReport() {
        metrics.forEach { (key, durations) ->
            println("$key: avg=${durations.average()}ms")
        }
    }
}

fun main() {
    // Example: Monitoring
    val metrics = MetricsCollector()
    val userServiceMonitored = UserServiceImpl().monitored(
        Monitor.Collectors.Printer(),
        metrics
    )
    userServiceMonitored.getUser("123")
    userServiceMonitored.validate("token")
    userServiceMonitored.syncLocalCache()
    metrics.printReport()
    
    // Example: Tracing
    val tracer: Tracer = // obtain from your OpenTelemetry setup
    val userServiceTraced = UserServiceImpl().traced(tracer)
    userServiceTraced.getUser("456")
    userServiceTraced.validate("another-token")
    userServiceTraced.syncLocalCache() // Span is ignored for this method
}
```

## Design Principles

- **Zero Reflection**: All monitoring and tracing code is generated at compile time
- **Type Safety**: Generated code is fully type-safe
- **Composability**: Collectors (monitoring) or Tracer usage is easily combined
- **Simplicity**: Minimal API surface with maximum utility
- **Performance**: Negligible runtime overhead

## Capture Mechanism

### Performance-Optimized Design

A key architectural feature of Monitorable and Traceable is the use of inline functions via an abstract class for capturing method metrics (and spans), rather than using interface-based virtual dispatch. For monitoring, it might look like:

```kotlin
abstract class Capturing(val collector: Monitor.Collector) {
    inline fun <T> capture(
        key: String,
        crossinline closure: () -> Result<T>
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
```

For tracing, a similar pattern is used (e.g., `SpanCapturing`) but creating and ending spans with OpenTelemetry.

### Capturing Process

1. **KSP generates a proxy class** that extends your service interface.
2. **Each monitored or traced method** is wrapped with capture functions.
3. **Method execution is timed or spanned** using Kotlin's `measureTimedValue` or OpenTelemetry.
4. **Exceptions** are automatically caught and recorded.
5. **Collected data** is passed to collectors (monitoring) or exported as spans (tracing).
6. The original result (or exception) is returned to the caller.

## License

TBD