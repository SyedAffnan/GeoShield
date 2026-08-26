import 'dart:async';

import 'package:geolocator/geolocator.dart';

/// Why a real GPS fix could not be produced. Every value maps to a distinct,
/// user-actionable state; the service never substitutes a placeholder coordinate.
enum DeviceLocationFailureReason {
  /// Android location services / GPS are switched off device-wide.
  servicesDisabled,

  /// The runtime permission prompt was declined this time.
  permissionDenied,

  /// The permission was declined permanently, so only app settings can restore it.
  permissionDeniedForever,

  /// The platform accepted the request but produced no fix inside the time budget.
  timeout,

  /// The platform reported a failure while acquiring the fix.
  unavailable,
}

/// Result of a single on-demand location request.
sealed class DeviceLocationResult {
  const DeviceLocationResult();
}

/// A real fix reported by the device's location provider.
final class DeviceLocationFix extends DeviceLocationResult {
  const DeviceLocationFix({
    required this.latitude,
    required this.longitude,
    required this.accuracy,
    required this.speed,
    required this.timestamp,
  });

  final double latitude;
  final double longitude;

  /// Horizontal accuracy in metres, when the platform reports one.
  final double? accuracy;

  /// Ground speed in metres per second, when the platform reports one.
  final double? speed;

  /// The moment the platform recorded the fix, in UTC.
  final DateTime timestamp;
}

final class DeviceLocationFailure extends DeviceLocationResult {
  const DeviceLocationFailure(this.reason);
  final DeviceLocationFailureReason reason;
}

/// Reads the phone's current GPS position. Permission is requested at most once
/// per call, so a denied prompt can never turn into a request loop.
abstract interface class DeviceLocationService {
  Future<DeviceLocationResult> currentPosition();

  /// Opens the Android app settings page so a permanently denied permission can
  /// be restored by the user.
  Future<void> openAppSettings();

  /// Opens the Android location settings page so GPS can be switched on.
  Future<void> openLocationSettings();
}

class GeolocatorDeviceLocationService implements DeviceLocationService {
  const GeolocatorDeviceLocationService({this.timeLimit = const Duration(seconds: 20)});

  final Duration timeLimit;

  @override
  Future<DeviceLocationResult> currentPosition() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      return const DeviceLocationFailure(DeviceLocationFailureReason.servicesDisabled);
    }

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      // Requested only when the current status is `denied`, never in a retry loop.
      permission = await Geolocator.requestPermission();
    }
    if (permission == LocationPermission.deniedForever) {
      return const DeviceLocationFailure(DeviceLocationFailureReason.permissionDeniedForever);
    }
    if (permission == LocationPermission.denied) {
      return const DeviceLocationFailure(DeviceLocationFailureReason.permissionDenied);
    }

    try {
      final position = await Geolocator.getCurrentPosition(
        locationSettings: LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: timeLimit,
        ),
      );
      return DeviceLocationFix(
        latitude: position.latitude,
        longitude: position.longitude,
        accuracy: position.accuracy,
        speed: position.speed,
        timestamp: position.timestamp.toUtc(),
      );
    } on LocationServiceDisabledException {
      return const DeviceLocationFailure(DeviceLocationFailureReason.servicesDisabled);
    } on PermissionDeniedException {
      return const DeviceLocationFailure(DeviceLocationFailureReason.permissionDenied);
    } on TimeoutException {
      return const DeviceLocationFailure(DeviceLocationFailureReason.timeout);
    } catch (_) {
      return const DeviceLocationFailure(DeviceLocationFailureReason.unavailable);
    }
  }

  @override
  Future<void> openAppSettings() => Geolocator.openAppSettings();

  @override
  Future<void> openLocationSettings() => Geolocator.openLocationSettings();
}
