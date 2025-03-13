package com.baksha.observability.core.logging

/**
 * A Logger interface with Lazy message evaluation example:
 * ```
 * logger.info { "this is $lazy evaluated string" }
 * ```
 */
public interface Logger {
    /**
     * Lazy add a log message if level enabled
     */
    public fun at(level: Level, marker: Marker? = null, block: LogEventBuilder.() -> Unit)
}

public class LogEventBuilder {
    public var message: String? = null
    public var cause: Throwable? = null
    public var payload: Map<String, Any?>? = null
}

/**
 * Lazy add a log message if isTraceEnabled is true
 */
public fun Logger.trace(message: () -> Any?): Unit =
    at(Level.Trace) { this.message = message.toStringSafe() }

/**
 * Lazy add a log message if isDebugEnabled is true
 */
public fun Logger.debug(message: () -> Any?): Unit =
    at(Level.Debug) { this.message = message.toStringSafe() }

/**
 * Lazy add a log message if isInfoEnabled is true
 */
public fun Logger.info(message: () -> Any?): Unit =
    at(Level.Info) { this.message = message.toStringSafe() }

/**
 * Lazy add a log message if isWarnEnabled is true
 */
public fun Logger.warn(message: () -> Any?): Unit =
    at(Level.Warn) { this.message = message.toStringSafe() }

/**
 * Lazy add a log message if isErrorEnabled is true
 */
public fun Logger.error(message: () -> Any?): Unit =
    at(Level.Error) { this.message = message.toStringSafe() }

/**
 * Lazy add a log message if isTraceEnabled is true
 */
public fun Logger.trace(throwable: Throwable?, message: () -> Any?): Unit =
    at(Level.Trace) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isDebugEnabled is true
 */
public fun Logger.debug(throwable: Throwable?, message: () -> Any?): Unit =
    at(Level.Debug) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isInfoEnabled is true
 */
public fun Logger.info(throwable: Throwable?, message: () -> Any?): Unit =
    at(Level.Info) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isWarnEnabled is true
 */
public fun Logger.warn(throwable: Throwable?, message: () -> Any?): Unit =
    at(Level.Warn) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isErrorEnabled is true
 */
public fun Logger.error(throwable: Throwable?, message: () -> Any?): Unit =
    at(Level.Error) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isTraceEnabled is true
 */
public fun Logger.trace(throwable: Throwable? = null, marker: Marker?, message: () -> Any?): Unit =
    at(Level.Trace, marker) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isDebugEnabled is true
 */
public fun Logger.debug(throwable: Throwable? = null, marker: Marker?, message: () -> Any?): Unit =
    at(Level.Debug, marker) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isInfoEnabled is true
 */
public fun Logger.info(throwable: Throwable? = null, marker: Marker?, message: () -> Any?): Unit =
    at(Level.Info, marker) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isWarnEnabled is true
 */
public fun Logger.warn(throwable: Throwable? = null, marker: Marker?, message: () -> Any?): Unit =
    at(Level.Warn, marker) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message if isErrorEnabled is true
 */
public fun Logger.error(throwable: Throwable? = null, marker: Marker?, message: () -> Any?): Unit =
    at(Level.Error, marker) {
        this.message = message.toStringSafe()
        this.cause = throwable
    }

/**
 * Lazy add a log message with throwable payload if isTraceEnabled is true
 */
public fun Logger.atTrace(marker: Marker?, block: LogEventBuilder.() -> Unit): Unit =
    at(Level.Trace, marker, block)

/**
 * Lazy add a log message with throwable payload if isTraceEnabled is true
 */
public fun Logger.atTrace(block: LogEventBuilder.() -> Unit): Unit = at(Level.Trace, null, block)

/**
 * Lazy add a log message with throwable payload if isDebugEnabled is true
 */
public fun Logger.atDebug(marker: Marker?, block: LogEventBuilder.() -> Unit): Unit =
    at(Level.Debug, marker, block)

/**
 * Lazy add a log message with throwable payload if isDebugEnabled is true
 */
public fun Logger.atDebug(block: LogEventBuilder.() -> Unit): Unit = at(Level.Debug, null, block)

/**
 * Lazy add a log message with throwable payload if isInfoEnabled is true
 */
public fun Logger.atInfo(marker: Marker?, block: LogEventBuilder.() -> Unit): Unit =
    at(Level.Info, marker, block)

/**
 * Lazy add a log message with throwable payload if isInfoEnabled is true
 */
public fun Logger.atInfo(block: LogEventBuilder.() -> Unit): Unit = at(Level.Info, null, block)

/**
 * Lazy add a log message with throwable payload if isWarnEnabled is true
 */
public fun Logger.atWarn(marker: Marker?, block: LogEventBuilder.() -> Unit): Unit =
    at(Level.Warn, marker, block)

/**
 * Lazy add a log message with throwable payload if isWarnEnabled is true
 */
public fun Logger.atWarn(block: LogEventBuilder.() -> Unit): Unit = at(Level.Warn, null, block)

/**
 * Lazy add a log message with throwable payload if isErrorEnabled is true
 */
public fun Logger.atError(marker: Marker?, block: LogEventBuilder.() -> Unit): Unit =
    at(Level.Error, marker, block)

/**
 * Lazy add a log message with throwable payload if isErrorEnabled is true
 */
public fun Logger.atError(block: LogEventBuilder.() -> Unit): Unit = at(Level.Error, null, block)