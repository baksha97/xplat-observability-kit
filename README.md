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
    override fun invoke(data: Monitor.Data) {
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
    
    override fun invoke(data: Monitor.Data) {
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

## License

TBD