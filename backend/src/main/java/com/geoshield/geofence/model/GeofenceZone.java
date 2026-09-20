package com.geoshield.geofence.model;

/**
 * Deterministic geofence zone classifications.
 */
public enum GeofenceZone {
    /** Outside both the pre-warning and core hazard envelopes. */
    OUTSIDE,

    /** Inside the informational pre-warning envelope (1,000 m entry boundary). */
    PRE_WARNING,

    /** Inside the acute core hazard envelope (500 m entry boundary). */
    CORE
}
