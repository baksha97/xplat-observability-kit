# Monitorable - Functional AOP for Kotlin

A modern, declarative approach to method monitoring in Kotlin using KSP (Kotlin Symbol Processing).

## Overview

Monitorable provides lightweight AOP capabilities through compile-time code generation, enabling method monitoring with:
- Zero runtime reflection
- Type-safe implementations
- Functional composition
- Declarative configuration

## Usage

1. Add the annotation to your interface:
```kotlin
@Monitorable
interface UserService {
    fun getUser(id: String): User
    suspend fun updateUser(user: User): Result<User>
}
```

2. Use the generated monitored extension:
```kotlin
val monitoredService = userService.monitored()
```

## Features

### Declarative Configuration

Configure monitoring behavior through annotations:
```kotlin
@Monitorable
interface PaymentService {
    fun processPayment(payment: Payment): Result<Transaction>
}
```

### Functional Composition

Chain monitoring collectors for different concerns:
```kotlin
val service = userService
    .monitored(MetricsCollector())
    .monitored(LoggingCollector())
    .monitored(TracingCollector())
```

Alternatively, if less proxying is preferred, we can leverage a built in `CompositeCollector`. Or create your own!
```kotlin
val service = userService
    .monitored(MetricsCollector(), LoggingCollector())
```

### Custom Collectors

Implement the `MonitorCollector` interface for custom monitoring:
```kotlin
class MetricsCollector : MonitorCollector {
    override fun onCollection(monitorData: MonitorData) {
        // Record metrics
    }
}
```

### Rich Monitoring Data

Monitor invocations with comprehensive data:
```kotlin
data class MonitorData(
    val methodName: String,
    val durationMillis: Long,
    val successful: Boolean,
    val exception: Throwable? = null,
    val metadata: Map<String, Any?> = emptyMap()
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
    implementation("com.example:monitorable-annotations:1.0.0")
}
```

## Design Principles

- **Declarative over Imperative**: Use annotations for configuration
- **Composition over Inheritance**: Stack monitors functionally
- **Type Safety**: Generated code is fully type-safe
- **Zero Reflection**: All monitoring code is generated at compile-time
- **Minimal Runtime**: No runtime dependencies beyond standard Kotlin libraries

## License

TBD
