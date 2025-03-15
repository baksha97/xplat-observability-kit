# Proposal for Span Tracking API

## Overview

This proposal outlines the API surface for tracking spans in a similar ergonomic manner as the existing `@Monitor.Collectable` annotation. The goal is to make span tracking intuitive and easy to integrate into existing codebases.

## Annotations

### `@Span.Trackable`

Marks an interface or class as eligible for span tracking. The annotation processor will generate a proxy that implements the interface or class and captures span data for all methods.

**Example usage:**
```kotlin
@Span.Trackable
interface UserService {
    fun getUser(id: String): User
}
```

### `@Span.Operation`

Customizes the span name for a specific function. When applied to a function in a `Trackable` interface or class, this annotation allows specifying a custom name for the span data instead of using the method name.

**Example usage:**
```kotlin
@Span.Operation("user.fetch")
fun getUser(id: String): User
```

## Data Structures

### `Span.Data`

Represents a single span event with execution metrics.

**Properties:**
- `key`: The identifier for the span operation
- `durationMillis`: The execution time in milliseconds
- `exception`: Any exception that occurred during execution, or null if successful

**Example usage:**
```kotlin
data class Data(
    val key: String,
    val durationMillis: Long,
    val exception: Throwable? = null,
)
```

## Collector Interface

### `Span.Collector`

Interface for collecting and processing span data. Implementations can define custom handling of span events, such as logging, metrics collection, or alerting.

**Example usage:**
```kotlin
fun interface Collector {
    fun collect(data: Data)
}
```

## Built-in Collectors

### `Span.Collectors`

Provides built-in implementations of `Collector`.

#### `Printer`

A simple collector that prints span data to standard output. Useful for debugging and development purposes.

**Example usage:**
```kotlin
val service = userService.tracked(Span.Collectors.Printer())
```

#### `Composite`

A collector that delegates to multiple other collectors. Useful for sending span data to multiple destinations simultaneously.

**Example usage:**
```kotlin
val service = userService.tracked(
    Span.Collectors.Printer(),
    myCustomMetricsCollector
)
```
