# Monitorable - Functional Method Monitoring for Kotlin

A lightweight, zero-reflection approach to method monitoring in Kotlin using KSP (Kotlin Symbol Processing).

## Overview

Monitorable provides simple but powerful method monitoring through compile-time code generation, featuring:
- Zero runtime reflection
- Compile-time generation
- Built-in error handling
- Composable collectors
- Minimal runtime overhead

## Usage

1. Add the Monitor annotation to your interface:
```kotlin
@Monitor.Collectable
interface AuthService {
    @Monitor.Function(name = "auth_user_get")
    fun getUser(): String
    
    @Monitor.Function(name = "auth_result_get")
    fun getResult(): Result<String>
}
```

2. Use the generated monitored extension:
```kotlin
// Single collector
val service = authService.monitored(Monitor.Collectors.Printer())

// Multiple collectors
val service = authService.monitored(
    Monitor.Collectors.Printer(),
    MetricsCollector()
)
```

## Features

### Built-in Error Handling

Monitorable handles both regular exceptions and Result-wrapped returns:
```kotlin
@Monitor.Collectable
interface UserService {
    // Regular methods - exceptions are caught and reported
    @Monitor.Function(name = "get_user")
    fun getUser(id: String): String
    
    // Result-returning methods - failures are tracked
    @Monitor.Function(name = "validate_user")
    fun validateUser(user: User): Result<Boolean>
}
```

### Simple Collector Interface

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

### Rich Monitoring Data

Track method execution with comprehensive data:
```kotlin
data class Data(
    val key: String,
    val durationMillis: Long,
    val exception: Throwable? = null,
)
```

### Built-in Collectors

Use pre-built collectors or combine them:
```kotlin
// Simple printing collector
val printer = Monitor.Collectors.Printer()

// Combine multiple collectors
val composite = Monitor.Collectors.Composite(
    Monitor.Collectors.Printer(),
    // Additional custom collectors...
)
```

## Setup

Add to your `build.gradle.kts`:
```kotlin
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("com.example:monitorable-processor:1.0.0")
    implementation("com.example:monitorable:1.0.0")
}
```

## Complete Example

```kotlin
@Monitor.Collectable
interface UserService {
    @Monitor.Function(name = "get_user")
    fun getUser(id: String): String

    @Monitor.Function(name = "validate")
    fun validate(token: String): Result<Boolean>
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
    val metrics = MetricsCollector()
    val service = UserServiceImpl().monitored(
        Monitor.Collectors.Printer(),
        metrics
    )
    
    // Use the service
    service.getUser("123")
    service.validate("token")
    
    // Print metrics
    metrics.printReport()
}
```

## Design Principles

- **Zero Reflection**: All monitoring code is generated at compile-time
- **Type Safety**: Generated code is fully type-safe
- **Composability**: Collectors can be easily combined
- **Simplicity**: Minimal API surface with maximum utility
- **Performance**: Negligible runtime overhead

## Capture Mechanism

### Performance-Optimized Design

A key architectural feature of Monitorable is its use of inline functions via an abstract class for capturing method metrics, rather than using interface-based virtual dispatch:

```kotlin
abstract class Capturing(val collector: Monitor.Collector) {
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
    inline fun <T> capture(
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
```

This design provides several critical performance benefits:

1. **Zero Virtual Dispatch**: Using inline functions eliminates virtual method call overhead that would occur with an interface-based approach

2. **Optimized Stack Traces**: Function inlining reduces stack trace complexity and improves exception handling performance

3. **Efficient Coroutine Support**: Inline functions allow the compiler to optimize suspend function handling without additional state machine overhead

4. **JVM Optimization**: The abstract class design enables better JVM inlining and escape analysis optimizations

The generated monitoring code leverages these optimizations to provide essentially zero-overhead method monitoring. When combined with compile-time code generation, this results in monitoring capabilities with negligible runtime impact.

### Capturing Process

The monitoring process follows these steps:

1. KSP generates a proxy class that extends your service interface
2. Each monitored method is wrapped with capture functions
3. Method execution is timed using Kotlin's `measureTimedValue`
4. Any exceptions are automatically caught and recorded
5. Timing and error data is passed to the collector(s)
6. The original result (or exception) is returned to the caller

This process happens with minimal overhead due to:
- Compile-time code generation (no reflection)
- Inlined capture functions (no virtual dispatch)
- Efficient exception handling
- Zero-copy metric collection

## License

TBD
