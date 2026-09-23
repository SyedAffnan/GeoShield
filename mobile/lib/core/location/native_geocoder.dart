import 'dart:async';
import 'package:flutter/services.dart';

/// Resolved address components from native platform Geocoder.
class GeocodedAddress {
  final String? locality;
  final String? subLocality;
  final String? district;
  final String? state;

  const GeocodedAddress({
    this.locality,
    this.subLocality,
    this.district,
    this.state,
  });

  /// Returns the most specific locality string available (locality or subLocality).
  String? get bestLocality {
    if (locality != null && locality!.trim().isNotEmpty) {
      return locality!.trim();
    }
    if (subLocality != null && subLocality!.trim().isNotEmpty) {
      return subLocality!.trim();
    }
    return null;
  }
}

/// Lightweight interface to native Android Geocoder over MethodChannel.
class NativeGeocoder {
  static const MethodChannel _channel =
      MethodChannel('com.geoshield.mobile/native_geocoder');

  /// Attempts to reverse-geocode coordinates into locality and district components.
  /// Times out after 4 seconds and returns null on any error.
  static Future<GeocodedAddress?> reverseGeocode(
    double latitude,
    double longitude,
  ) async {
    try {
      final dynamic raw = await _channel.invokeMethod('reverseGeocode', {
        'latitude': latitude,
        'longitude': longitude,
      }).timeout(const Duration(seconds: 4));

      if (raw is Map) {
        return GeocodedAddress(
          locality: raw['locality'] as String?,
          subLocality: raw['subLocality'] as String?,
          district: raw['subAdminArea'] as String?,
          state: raw['adminArea'] as String?,
        );
      }
      return null;
    } catch (_) {
      // Geocoding failure or timeout; return null so server fallback takes over.
      return null;
    }
  }
}
