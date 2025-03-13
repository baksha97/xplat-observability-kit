package com.baksha.observability.core.logging

/**
 * We can optionally handle messages that throw errors, but for now we will expect this not to occur
 */
internal fun (() -> Any?).toStringSafe(): String = runCatching {
    invoke().toString()
}.getOrElse { "Failed to generate message with: $it" }