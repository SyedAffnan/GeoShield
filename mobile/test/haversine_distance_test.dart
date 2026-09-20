import 'dart:math' as math;
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/geofencing/haversine_distance.dart';

void main() {
  group('HaversineDistance', () {
    const numericalToleranceMeters = 0.001; // 1 mm

    test('Zero distance for identical coordinates', () {
      final dist = HaversineDistance.distanceMeters(28.6139, 77.2090, 28.6139, 77.2090);
      expect(dist, 0.0);
    });

    test('Haversine distance between New Delhi and Jaipur is accurate', () {
      // New Delhi: 28.6139, 77.2090; Jaipur: 26.9124, 75.7873
      final meters = HaversineDistance.distanceMeters(28.6139, 77.2090, 26.9124, 75.7873);
      final km = HaversineDistance.distanceKm(28.6139, 77.2090, 26.9124, 75.7873);

      expect(km, inInclusiveRange(235.0, 242.0));
      expect((meters / 1000.0 - km).abs(), lessThan(numericalToleranceMeters));
    });

    test('Equatorial quarter-turn equals pi/2 * R', () {
      final dist = HaversineDistance.distanceMeters(0.0, 0.0, 0.0, 90.0);
      final expected = (math.pi / 2.0) * HaversineDistance.earthRadiusMeters;
      expect((dist - expected).abs(), lessThan(numericalToleranceMeters));
    });

    test('Pole to equator quarter-turn equals pi/2 * R', () {
      final dist = HaversineDistance.distanceMeters(0.0, 0.0, 90.0, 0.0);
      final expected = (math.pi / 2.0) * HaversineDistance.earthRadiusMeters;
      expect((dist - expected).abs(), lessThan(numericalToleranceMeters));
    });

    test('Rejects out of bounds coordinates', () {
      expect(() => HaversineDistance.validateCoordinates(91.0, 77.0), throwsArgumentError);
      expect(() => HaversineDistance.validateCoordinates(-90.1, 77.0), throwsArgumentError);
      expect(() => HaversineDistance.validateCoordinates(28.0, 180.1), throwsArgumentError);
      expect(() => HaversineDistance.validateCoordinates(28.0, -180.5), throwsArgumentError);
    });

    test('Rejects NaN and Infinity coordinates', () {
      expect(() => HaversineDistance.validateCoordinates(double.nan, 77.0), throwsArgumentError);
      expect(() => HaversineDistance.validateCoordinates(28.0, double.infinity), throwsArgumentError);
      expect(() => HaversineDistance.validateCoordinates(double.negativeInfinity, 77.0), throwsArgumentError);
    });

    test('Shared canonical parity vector with Java GeoDistanceUtil (within 1 mm)', () {
      // Point A: Mumbai (18.9220, 72.8347), Point B: Gateway of India area offset (18.9300, 72.8400)
      final meters = HaversineDistance.distanceMeters(18.9220, 72.8347, 18.9300, 72.8400);
      // Canonical spherical Haversine formula with R = 6,371,000.0 m evaluates to 1049.806 meters
      expect((meters - 1049.806).abs(), lessThan(numericalToleranceMeters));
    });
  });
}
