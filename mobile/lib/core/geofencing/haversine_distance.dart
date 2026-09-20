import 'dart:math' as math;

/// Canonical geodetic distance calculation using the spherical Haversine formula.
///
/// Parity requirements:
/// - Earth radius: 6,371,000.0 meters.
/// - Latitude: [-90.0, 90.0].
/// - Longitude: [-180.0, 180.0].
/// - Rejects NaN, Infinity, and out-of-bounds coordinates.
/// - No (0.0, 0.0) fallback or automatic coordinate substitution is permitted.
/// - Parity with Java GeoDistanceUtil within 1 mm (0.001 m).
abstract final class HaversineDistance {
  static const double earthRadiusMeters = 6371000.0;

  /// Calculates spherical distance between two points in meters.
  static double distanceMeters(
    double lat1,
    double lon1,
    double lat2,
    double lon2,
  ) {
    validateCoordinates(lat1, lon1);
    validateCoordinates(lat2, lon2);

    if (lat1 == lat2 && lon1 == lon2) {
      return 0.0;
    }

    final dLat = _toRadians(lat2 - lat1);
    final dLon = _toRadians(lon2 - lon1);
    final lat1Rad = _toRadians(lat1);
    final lat2Rad = _toRadians(lat2);

    final sinDLat = math.sin(dLat / 2.0);
    final sinDLon = math.sin(dLon / 2.0);

    double a = sinDLat * sinDLat +
        math.cos(lat1Rad) * math.cos(lat2Rad) * sinDLon * sinDLon;

    if (a < 0.0) {
      a = 0.0;
    } else if (a > 1.0) {
      a = 1.0;
    }

    final c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a));
    return earthRadiusMeters * c;
  }

  /// Calculates spherical distance between two points in kilometers.
  static double distanceKm(
    double lat1,
    double lon1,
    double lat2,
    double lon2,
  ) {
    return distanceMeters(lat1, lon1, lat2, lon2) / 1000.0;
  }

  /// Validates coordinate boundaries and finite number representation.
  static void validateCoordinates(double lat, double lon) {
    if (lat.isNaN || lat.isInfinite) {
      throw ArgumentError.value(lat, 'lat', 'Latitude cannot be NaN or Infinite');
    }
    if (lon.isNaN || lon.isInfinite) {
      throw ArgumentError.value(lon, 'lon', 'Longitude cannot be NaN or Infinite');
    }
    if (lat < -90.0 || lat > 90.0) {
      throw ArgumentError.value(lat, 'lat', 'Latitude out of bounds [-90.0, 90.0]');
    }
    if (lon < -180.0 || lon > 180.0) {
      throw ArgumentError.value(lon, 'lon', 'Longitude out of bounds [-180.0, 180.0]');
    }
  }

  static double _toRadians(double degrees) => degrees * (math.pi / 180.0);
}
