package com.baksha.observability.core.logging

public sealed class Level {
    public data object Trace: Level()
    public data object Debug: Level()
    public data object Info: Level()
    public data object Warn: Level()
    public data object Error: Level()
    public companion object
}

