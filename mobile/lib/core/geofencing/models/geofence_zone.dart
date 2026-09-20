/// Deterministic geofence zone classifications.
enum GeofenceZone {
  /// Outside both pre-warning (1,000 m) and core (500 m) envelopes.
  outside,

  /// Inside the informational pre-warning envelope.
  preWarning,

  /// Inside the acute core hazard envelope.
  core,
}
