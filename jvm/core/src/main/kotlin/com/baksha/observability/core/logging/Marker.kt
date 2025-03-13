package com.baksha.observability.core.logging

/** A independent marker to enrich log statements. */
public interface Marker {
    public val name: String
}

public data class SimpleMarker(override val name: String) : Marker