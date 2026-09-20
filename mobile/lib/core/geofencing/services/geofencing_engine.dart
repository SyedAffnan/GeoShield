import 'dart:math' as math;

import '../../location/device_location_service.dart';
import '../haversine_distance.dart';
import '../models/geofence_zone.dart';
import 'hazard_geometry_provider.dart';

/// Runtime tracking state for a specific hazard on device.
final class HazardTrackingState {
  const HazardTrackingState({
    required this.hazardId,
    required this.currentZone,
    required this.previousZone,
    required this.lastDistanceMeters,
    required this.lastEvaluatedAt,
    this.lastAlertAt,
  });

  final String hazardId;
  final GeofenceZone currentZone;
  final GeofenceZone previousZone;
  final double lastDistanceMeters;
  final DateTime lastEvaluatedAt;

  /// Timestamp of the last emitted alert notification for this hazard.
  final DateTime? lastAlertAt;

  HazardTrackingState copyWith({
    GeofenceZone? currentZone,
    GeofenceZone? previousZone,
    double? lastDistanceMeters,
    DateTime? lastEvaluatedAt,
    DateTime? lastAlertAt,
  }) {
    return HazardTrackingState(
      hazardId: hazardId,
      currentZone: currentZone ?? this.currentZone,
      previousZone: previousZone ?? this.previousZone,
      lastDistanceMeters: lastDistanceMeters ?? this.lastDistanceMeters,
      lastEvaluatedAt: lastEvaluatedAt ?? this.lastEvaluatedAt,
      lastAlertAt: lastAlertAt ?? this.lastAlertAt,
    );
  }
}

/// Transition event emitted when a valid zone boundary transition occurs.
final class GeofenceTransitionEvent {
  const GeofenceTransitionEvent({
    required this.hazardId,
    required this.hazardName,
    required this.previousZone,
    required this.nextZone,
    required this.distanceMeters,
    required this.transitionOccurred,
    required this.cooldownSuppressed,
    required this.isSynthetic,
    required this.evaluatedAt,
  });

  final String hazardId;
  final String hazardName;
  final GeofenceZone previousZone;
  final GeofenceZone nextZone;
  final double distanceMeters;
  final bool transitionOccurred;
  final bool cooldownSuppressed;
  final bool isSynthetic;
  final DateTime evaluatedAt;

  @override
  String toString() =>
      'GeofenceTransitionEvent(id: $hazardId, $previousZone -> $nextZone, '
      'dist: ${distanceMeters.toStringAsFixed(1)}m, changed: $transitionOccurred, '
      'cooldownSuppressed: $cooldownSuppressed, synthetic: $isSynthetic)';
}

/// Mobile dual-zone geofencing engine.
///
/// Responsibilities:
/// - Sole owner of runtime per-hazard zone state across GPS fixes.
/// - Sole owner of alert cooldown timestamps.
/// - State transitions ALWAYS update the tracked state immediately.
/// - Cooldown ONLY suppresses notification emission.
class GeofencingEngine {
  GeofencingEngine({
    required this.hazardProvider,
    this.cooldownDuration = defaultCooldownDuration,
    DateTime Function()? nowFn,
  }) : _nowFn = nowFn ?? DateTime.now;

  final HazardGeometryProvider hazardProvider;
  final DateTime Function() _nowFn;

  /// Default engineering starting parameter for alert cooldown.
  /// Marked explicitly as TO-BE-VALIDATED per project governance.
  static const Duration defaultCooldownDuration = Duration(seconds: 900); // 15 minutes

  final Duration cooldownDuration;

  /// Runtime tracked state per hazard ID (in memory only).
  final Map<String, HazardTrackingState> _trackingStates = {};

  Map<String, HazardTrackingState> get trackingStates =>
      Map.unmodifiable(_trackingStates);

  /// Resets all local tracking state.
  void reset() => _trackingStates.clear();

  /// Evaluates a location fix against all active hazards.
  /// Returns a list of transition events for any zone boundary crossings.
  Future<List<GeofenceTransitionEvent>> evaluateLocation(
    DeviceLocationFix fix,
  ) async {
    if (!isLocationValid(fix, _nowFn())) {
      return const [];
    }

    final hazards = await hazardProvider.getActiveHazards();
    if (hazards.isEmpty) {
      return const [];
    }

    final events = <GeofenceTransitionEvent>[];

    for (final hazard in hazards) {
      if (!hazard.isActive) continue;

      final distance = HaversineDistance.distanceMeters(
        fix.latitude,
        fix.longitude,
        hazard.latitude,
        hazard.longitude,
      );

      final currentState = _trackingStates[hazard.id];
      final currentZone = currentState?.currentZone ?? GeofenceZone.outside;

      final double h = math.max(
        50.0,
        fix.accuracy != null ? 2.0 * fix.accuracy! : 50.0,
      );

      final rCoreExit = hazard.coreRadiusMeters + h;
      final rPreExit = hazard.preWarningRadiusMeters + h;

      final nextZone = computeNextZone(
        currentZone,
        distance,
        hazard.coreRadiusMeters,
        hazard.preWarningRadiusMeters,
        rCoreExit,
        rPreExit,
      );

      final transitionOccurred = currentZone != nextZone;

      // Determine alert eligibility and cooldown suppression
      bool cooldownSuppressed = false;
      DateTime? updatedAlertAt = currentState?.lastAlertAt;

      final isAlertEligible = transitionOccurred &&
          (nextZone == GeofenceZone.core || nextZone == GeofenceZone.preWarning);

      if (isAlertEligible) {
        final lastAlert = currentState?.lastAlertAt;
        final inCooldown = lastAlert != null &&
            fix.timestamp.difference(lastAlert) < cooldownDuration;

        if (inCooldown) {
          cooldownSuppressed = true;
        } else {
          cooldownSuppressed = false;
          updatedAlertAt = fix.timestamp;
        }
      }

      // STATE MACHINE RULE: State updates unconditionally regardless of cooldown!
      _trackingStates[hazard.id] = HazardTrackingState(
        hazardId: hazard.id,
        currentZone: nextZone,
        previousZone: currentZone,
        lastDistanceMeters: distance,
        lastEvaluatedAt: fix.timestamp,
        lastAlertAt: updatedAlertAt,
      );

      if (transitionOccurred) {
        events.add(GeofenceTransitionEvent(
          hazardId: hazard.id,
          hazardName: hazard.name,
          previousZone: currentZone,
          nextZone: nextZone,
          distanceMeters: distance,
          transitionOccurred: true,
          cooldownSuppressed: cooldownSuppressed,
          isSynthetic: hazard.isSynthetic,
          evaluatedAt: fix.timestamp,
        ));
      }
    }

    return events;
  }

  /// Evaluates next zone using identical state-dependent hysteresis rules as Java.
  static GeofenceZone computeNextZone(
    GeofenceZone currentZone,
    double distance,
    double coreRadius,
    double preWarningRadius,
    double rCoreExit,
    double rPreExit,
  ) {
    switch (currentZone) {
      case GeofenceZone.outside:
        if (distance <= coreRadius) {
          return GeofenceZone.core;
        } else if (distance <= preWarningRadius) {
          return GeofenceZone.preWarning;
        } else {
          return GeofenceZone.outside;
        }

      case GeofenceZone.preWarning:
        if (distance <= coreRadius) {
          return GeofenceZone.core; // Strict entry, H never expands entry
        } else if (distance > rPreExit) {
          return GeofenceZone.outside; // Exit with hysteresis
        } else {
          return GeofenceZone.preWarning;
        }

      case GeofenceZone.core:
        if (distance > rPreExit) {
          return GeofenceZone.outside; // Direct exit
        } else if (distance > rCoreExit) {
          return GeofenceZone.preWarning; // Exit with hysteresis
        } else {
          return GeofenceZone.core;
        }
    }
  }

  /// Enforces Phase 0.2 location validity rules.
  static bool isLocationValid(DeviceLocationFix fix, [DateTime? referenceTime]) {
    try {
      HaversineDistance.validateCoordinates(fix.latitude, fix.longitude);
    } catch (_) {
      return false;
    }

    final now = (referenceTime ?? DateTime.now()).toUtc();
    final fixTime = fix.timestamp.toUtc();

    // Reject future timestamps strictly (no future leeway permitted per Phase 0.2)
    if (fixTime.isAfter(now)) {
      return false;
    }

    // Reject stale fixes (>15 minutes old per Phase 0.2)
    if (now.difference(fixTime) > const Duration(minutes: 15)) {
      return false;
    }

    // Reject unacceptable accuracy (>100 meters per Phase 0.2)
    if (fix.accuracy != null) {
      if (fix.accuracy!.isNaN ||
          fix.accuracy!.isInfinite ||
          fix.accuracy! < 0.0 ||
          fix.accuracy! > 100.0) {
        return false;
      }
    }

    return true;
  }
}
