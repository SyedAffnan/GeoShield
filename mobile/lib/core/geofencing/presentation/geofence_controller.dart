import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../di/providers.dart';
import '../../location/device_location_service.dart';
import '../models/geofence_zone.dart';
import '../services/geofence_notification_service.dart';
import '../services/geofencing_engine.dart';

/// State representation for geofence monitoring.
sealed class GeofenceState {
  const GeofenceState();
}

final class GeofenceInitial extends GeofenceState {
  const GeofenceInitial();
}

final class GeofenceReady extends GeofenceState {
  const GeofenceReady({
    required this.activeZone,
    this.nearestHazardId,
    this.nearestHazardName,
    this.nearestDistanceMeters,
    this.isSynthetic = false,
    required this.evaluatedAt,
  });

  /// Most severe zone across all evaluated hazards (core > preWarning > outside).
  final GeofenceZone activeZone;
  final String? nearestHazardId;
  final String? nearestHazardName;
  final double? nearestDistanceMeters;
  final bool isSynthetic;
  final DateTime evaluatedAt;
}

final class GeofenceUnavailable extends GeofenceState {
  const GeofenceUnavailable(this.reason);
  final String reason;
}

/// Riverpod controller coordinating location updates, geofence evaluation, and local notifications.
class GeofenceController extends Notifier<GeofenceState> {
  GeofenceController({
    GeofencingEngine? engine,
    GeofenceNotificationService? notificationService,
  })  : _engineOverride = engine,
        _notificationServiceOverride = notificationService;

  final GeofencingEngine? _engineOverride;
  final GeofenceNotificationService? _notificationServiceOverride;

  GeofencingEngine get _engine =>
      _engineOverride ?? ref.read(geofencingEngineProvider);
  GeofenceNotificationService get _notificationService =>
      _notificationServiceOverride ??
      ref.read(geofenceNotificationServiceProvider);

  @override
  GeofenceState build() {
    Future.microtask(() => _notificationService.initialize());
    return const GeofenceInitial();
  }

  /// Processes a new location fix from device location service.
  Future<void> processLocationFix(DeviceLocationFix fix) async {
    if (!GeofencingEngine.isLocationValid(fix)) {
      state = const GeofenceUnavailable('Location fix does not meet validity criteria.');
      return;
    }

    final events = await _engine.evaluateLocation(fix);

    // Dispatch notifications for all eligible transitions
    for (final event in events) {
      await _notificationService.handleTransition(event);
    }

    // Determine current overall device geofence state
    final hazards = await _engine.hazardProvider.getActiveHazards();
    if (hazards.isEmpty) {
      state = GeofenceReady(
        activeZone: GeofenceZone.outside,
        evaluatedAt: fix.timestamp,
      );
      return;
    }

    GeofenceZone highestZone = GeofenceZone.outside;
    String? nearestId;
    String? nearestName;
    double? minDistance;
    bool anySynthetic = false;

    // First determine the highest active zone across all hazards
    for (final hazard in hazards) {
      final tracking = _engine.trackingStates[hazard.id];
      if (tracking == null) continue;

      if (tracking.currentZone == GeofenceZone.core) {
        highestZone = GeofenceZone.core;
      } else if (tracking.currentZone == GeofenceZone.preWarning &&
          highestZone != GeofenceZone.core) {
        highestZone = GeofenceZone.preWarning;
      }
    }

    // Select the nearest hazard within the highest zone (or nearest overall if outside)
    for (final hazard in hazards) {
      final tracking = _engine.trackingStates[hazard.id];
      if (tracking == null) continue;

      final matchesZone = highestZone == GeofenceZone.outside ||
          tracking.currentZone == highestZone;

      if (matchesZone &&
          (minDistance == null || tracking.lastDistanceMeters < minDistance)) {
        minDistance = tracking.lastDistanceMeters;
        nearestId = hazard.id;
        nearestName = hazard.name;
        anySynthetic = hazard.isSynthetic;
      }
    }

    state = GeofenceReady(
      activeZone: highestZone,
      nearestHazardId: nearestId,
      nearestHazardName: nearestName,
      nearestDistanceMeters: minDistance,
      isSynthetic: anySynthetic,
      evaluatedAt: fix.timestamp,
    );
  }

  /// Sets unavailable status when location acquisition fails.
  void setUnavailable(String reason) {
    state = GeofenceUnavailable(reason);
  }
}
