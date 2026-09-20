import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/geofencing/haversine_distance.dart';
import 'package:geoshield_mobile/core/geofencing/models/geofence_zone.dart';
import 'package:geoshield_mobile/core/geofencing/models/hazard_geometry.dart';
import 'package:geoshield_mobile/core/geofencing/services/geofencing_engine.dart';
import 'package:geoshield_mobile/core/geofencing/services/hazard_geometry_provider.dart';
import 'package:geoshield_mobile/core/location/device_location_service.dart';

class SingleHazardProvider implements HazardGeometryProvider {
  SingleHazardProvider(this.hazard);

  final HazardGeometry hazard;

  @override
  Future<List<HazardGeometry>> getActiveHazards() async => [hazard];
}

class MultiHazardProvider implements HazardGeometryProvider {
  MultiHazardProvider(this.hazards);

  final List<HazardGeometry> hazards;

  @override
  Future<List<HazardGeometry>> getActiveHazards() async => hazards;
}

void main() {
  double latForDistanceMeters(double d) {
    double lat = (d / HaversineDistance.earthRadiusMeters) * (180.0 / math.pi);
    while (HaversineDistance.distanceMeters(lat, 0.0, 0.0, 0.0) > d) {
      lat -= 1e-15;
    }
    return lat;
  }

  final testHazard = HazardGeometry(
    id: 'TEST-HZ-001',
    name: 'Synthetic Test Hazard 001',
    latitude: 0.0,
    longitude: 0.0,
    coreRadiusMeters: 500.0,
    preWarningRadiusMeters: 1000.0,
    isSynthetic: true,
    dataSource: 'TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION',
  );

  group('GeofencingEngine - Golden Vectors V1 to V11 (acc = 80m -> H = 160m)', () {
    // With acc = 80m:
    // Core Entry <= 500m (fixed)
    // Core Exit > 660m (500 + 160)
    // Pre-Warning Entry <= 1000m (fixed)
    // Pre-Warning Exit > 1160m (1000 + 160)

    test('V1: previous=PRE_WARNING, d=550m, acc=80m -> PRE_WARNING (H never expands Core entry)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        550.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.preWarning));
    });

    test('V2: previous=PRE_WARNING, d=499m, acc=80m -> CORE (Crosses fixed Core entry)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        499.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.core));
    });

    test('V3: previous=CORE, d=550m, acc=80m -> CORE (Exit hysteresis retains Core)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.core,
        550.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.core));
    });

    test('V4: previous=CORE, d=661m, acc=80m -> PRE_WARNING (Crosses expanded Core exit)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.core,
        661.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.preWarning));
    });

    test('V5: previous=PRE_WARNING, d=1161m, acc=80m -> OUTSIDE (Crosses expanded Pre-Warning exit)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        1161.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.outside));
    });

    test('V6: previous=PRE_WARNING, d=501m, acc=80m -> PRE_WARNING (Boundary test)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        501.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.preWarning));
    });

    test('V7: previous=OUTSIDE, d=1001m, acc=80m -> OUTSIDE (Boundary test)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.outside,
        1001.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.outside));
    });

    test('V8: previous=OUTSIDE, d=999m, acc=80m -> PRE_WARNING (Crosses fixed Pre-Warning entry)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.outside,
        999.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.preWarning));
    });

    test('V9: previous=OUTSIDE, d=450m, acc=80m -> CORE (Direct entry into Core)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.outside,
        450.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.core));
    });

    test('V10: previous=CORE, d=1161m, acc=80m -> OUTSIDE (Direct exit to Outside)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.core,
        1161.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.outside));
    });

    test('V11: previous=PRE_WARNING, d=1050m, acc=80m -> PRE_WARNING (Exit hysteresis retains Pre-Warning)', () {
      final next = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        1050.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(next, equals(GeofenceZone.preWarning));
    });
  });

  group('GeofencingEngine - Exact Boundary Points Verification (500m, 1000m, 660m, 1160m)', () {
    test('Exact boundary 500m: d <= 500.0m is CORE; d = 501.0m remains PRE_WARNING', () {
      final at500 = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        500.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(at500, equals(GeofenceZone.core));

      final at501 = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        501.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(at501, equals(GeofenceZone.preWarning));
    });

    test('Exact boundary 1000m: d <= 1000.0m is PRE_WARNING; d = 1001.0m remains OUTSIDE', () {
      final at1000 = GeofencingEngine.computeNextZone(
        GeofenceZone.outside,
        1000.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(at1000, equals(GeofenceZone.preWarning));

      final at1001 = GeofencingEngine.computeNextZone(
        GeofenceZone.outside,
        1001.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(at1001, equals(GeofenceZone.outside));
    });

    test('Exact boundary 660m (acc=80m, H=160m): d = 660.0m remains CORE; d = 661.0m exits to PRE_WARNING', () {
      final at660 = GeofencingEngine.computeNextZone(
        GeofenceZone.core,
        660.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(at660, equals(GeofenceZone.core));

      final at661 = GeofencingEngine.computeNextZone(
        GeofenceZone.core,
        661.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(at661, equals(GeofenceZone.preWarning));
    });

    test('Exact boundary 1160m (acc=80m, H=160m): d = 1160.0m remains PRE_WARNING; d = 1161.0m exits to OUTSIDE', () {
      final at1160 = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        1160.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(at1160, equals(GeofenceZone.preWarning));

      final at1161 = GeofencingEngine.computeNextZone(
        GeofenceZone.preWarning,
        1161.0,
        500.0,
        1000.0,
        660.0,
        1160.0,
      );
      expect(at1161, equals(GeofenceZone.outside));
    });

    test('Accuracy formula H = max(50.0, 2 * acc)', () {
      // acc = null or 20m -> H = 50m
      expect(math.max(50.0, 2.0 * 20.0), equals(50.0));
      // acc = 80m -> H = 160m
      expect(math.max(50.0, 2.0 * 80.0), equals(160.0));
      // acc = 100m -> H = 200m
      expect(math.max(50.0, 2.0 * 100.0), equals(200.0));
    });
  });

  group('GeofencingEngine - Location Validity (Phase 0.2)', () {
    final refTime = DateTime.utc(2026, 9, 20, 12, 0, 0);

    test('Rejects coordinates exceeding range', () {
      final fix = DeviceLocationFix(
        latitude: 95.0,
        longitude: 0.0,
        accuracy: 10.0,
        speed: 0.0,
        timestamp: refTime,
      );
      expect(GeofencingEngine.isLocationValid(fix, refTime), isFalse);
    });

    test('Rejects stale location fix older than 15 minutes per Phase 0.2', () {
      // Older than 15m by 1s
      final fixStale = DeviceLocationFix(
        latitude: 0.0,
        longitude: 0.0,
        accuracy: 10.0,
        speed: 0.0,
        timestamp: refTime.subtract(const Duration(minutes: 15, seconds: 1)),
      );
      expect(GeofencingEngine.isLocationValid(fixStale, refTime), isFalse);

      // Boundary at exactly 15m is accepted
      final fixBoundary = DeviceLocationFix(
        latitude: 0.0,
        longitude: 0.0,
        accuracy: 10.0,
        speed: 0.0,
        timestamp: refTime.subtract(const Duration(minutes: 15)),
      );
      expect(GeofencingEngine.isLocationValid(fixBoundary, refTime), isTrue);
    });

    test('Rejects future dated location fix strictly (including +1 second) per Phase 0.2', () {
      // +1s future fix rejected
      final fixFuture1s = DeviceLocationFix(
        latitude: 0.0,
        longitude: 0.0,
        accuracy: 10.0,
        speed: 0.0,
        timestamp: refTime.add(const Duration(seconds: 1)),
      );
      expect(GeofencingEngine.isLocationValid(fixFuture1s, refTime), isFalse);

      // +2m future fix rejected
      final fixFuture2m = DeviceLocationFix(
        latitude: 0.0,
        longitude: 0.0,
        accuracy: 10.0,
        speed: 0.0,
        timestamp: refTime.add(const Duration(minutes: 2)),
      );
      expect(GeofencingEngine.isLocationValid(fixFuture2m, refTime), isFalse);
    });

    test('Accepts accuracy exactly 100m and rejects accuracy > 100m per Phase 0.2', () {
      final fix100 = DeviceLocationFix(
        latitude: 0.0,
        longitude: 0.0,
        accuracy: 100.0,
        speed: 0.0,
        timestamp: refTime,
      );
      expect(GeofencingEngine.isLocationValid(fix100, refTime), isTrue);

      final fix100Point1 = DeviceLocationFix(
        latitude: 0.0,
        longitude: 0.0,
        accuracy: 100.1,
        speed: 0.0,
        timestamp: refTime,
      );
      expect(GeofencingEngine.isLocationValid(fix100Point1, refTime), isFalse);
    });

    test('Accepts null accuracy per Phase 0.2 compatibility', () {
      final fixNullAcc = DeviceLocationFix(
        latitude: 0.0,
        longitude: 0.0,
        accuracy: null,
        speed: 0.0,
        timestamp: refTime,
      );
      expect(GeofencingEngine.isLocationValid(fixNullAcc, refTime), isTrue);
    });

    test('Rejects negative accuracy per Phase 0.2', () {
      final fixNegAcc = DeviceLocationFix(
        latitude: 0.0,
        longitude: 0.0,
        accuracy: -1.0,
        speed: 0.0,
        timestamp: refTime,
      );
      expect(GeofencingEngine.isLocationValid(fixNegAcc, refTime), isFalse);
    });

    test('Accepts valid location fix within thresholds', () {
      final fix = DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: 15.0,
        speed: 0.0,
        timestamp: refTime,
      );
      expect(GeofencingEngine.isLocationValid(fix, refTime), isTrue);
    });
  });

  group('GeofencingEngine - Cooldown Decoupling and State Updates', () {
    test('State updates unconditionally to CORE even when notification cooldown is active', () async {
      final t0 = DateTime.utc(2026, 9, 20, 12, 0, 0);
      var currentTime = t0;

      final engine = GeofencingEngine(
        hazardProvider: SingleHazardProvider(testHazard),
        cooldownDuration: const Duration(seconds: 900),
        nowFn: () => currentTime,
      );

      // Fix 1: Enter PRE_WARNING at 800m (t=0)
      final fix1 = DeviceLocationFix(
        latitude: latForDistanceMeters(800.0),
        longitude: 0.0,
        accuracy: 20.0,
        speed: 5.0,
        timestamp: t0,
      );

      final events1 = await engine.evaluateLocation(fix1);
      expect(events1.length, equals(1));
      expect(events1.first.previousZone, equals(GeofenceZone.outside));
      expect(events1.first.nextZone, equals(GeofenceZone.preWarning));
      expect(events1.first.cooldownSuppressed, isFalse);

      final state1 = engine.trackingStates['TEST-HZ-001'];
      expect(state1, isNotNull);
      expect(state1!.currentZone, equals(GeofenceZone.preWarning));
      expect(state1.lastAlertAt, equals(t0));

      // Fix 2: 60s later (t=60s), enter CORE at 400m
      // Cooldown (900s) is active, but state MUST update to CORE!
      final t1 = t0.add(const Duration(seconds: 60));
      currentTime = t1;
      final fix2 = DeviceLocationFix(
        latitude: latForDistanceMeters(400.0),
        longitude: 0.0,
        accuracy: 20.0,
        speed: 5.0,
        timestamp: t1,
      );

      final events2 = await engine.evaluateLocation(fix2);
      expect(events2.length, equals(1));
      expect(events2.first.previousZone, equals(GeofenceZone.preWarning));
      expect(events2.first.nextZone, equals(GeofenceZone.core));
      expect(events2.first.transitionOccurred, isTrue);
      // Cooldown suppresses notification emission:
      expect(events2.first.cooldownSuppressed, isTrue);

      // STATE CHECK: Tracked state MUST be CORE!
      final state2 = engine.trackingStates['TEST-HZ-001'];
      expect(state2, isNotNull);
      expect(state2!.currentZone, equals(GeofenceZone.core));
      // Alert timestamp remains t0 because this alert was suppressed
      expect(state2.lastAlertAt, equals(t0));

      // Fix 3: 120s later (t=120s), exit to PRE_WARNING at 600m
      final t2 = t0.add(const Duration(seconds: 120));
      currentTime = t2;
      final fix3 = DeviceLocationFix(
        latitude: latForDistanceMeters(600.0),
        longitude: 0.0,
        accuracy: 20.0,
        speed: 5.0,
        timestamp: t2,
      );

      final events3 = await engine.evaluateLocation(fix3);
      expect(events3.length, equals(1));
      expect(events3.first.previousZone, equals(GeofenceZone.core));
      expect(events3.first.nextZone, equals(GeofenceZone.preWarning));
      expect(events3.first.transitionOccurred, isTrue);

      final state3 = engine.trackingStates['TEST-HZ-001'];
      expect(state3, isNotNull);
      expect(state3!.currentZone, equals(GeofenceZone.preWarning));

      // Fix 4: After cooldown expires (t=950s), re-enter CORE at 300m
      final t3 = t0.add(const Duration(seconds: 950));
      currentTime = t3;
      final fix4 = DeviceLocationFix(
        latitude: latForDistanceMeters(300.0),
        longitude: 0.0,
        accuracy: 20.0,
        speed: 5.0,
        timestamp: t3,
      );

      final events4 = await engine.evaluateLocation(fix4);
      expect(events4.length, equals(1));
      expect(events4.first.previousZone, equals(GeofenceZone.preWarning));
      expect(events4.first.nextZone, equals(GeofenceZone.core));
      expect(events4.first.cooldownSuppressed, isFalse); // Cooldown expired!
      expect(engine.trackingStates['TEST-HZ-001']!.lastAlertAt, equals(t3));
    });

    test('Multi-hazard tracking and cooldowns remain strictly independent', () async {
      final hazardA = HazardGeometry(
        id: 'TEST-HZ-001',
        name: 'Hazard A',
        latitude: 0.0,
        longitude: 0.0,
        coreRadiusMeters: 500.0,
        preWarningRadiusMeters: 1000.0,
        isSynthetic: true,
        dataSource: 'TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION',
      );

      // Hazard B located far away
      final hazardB = HazardGeometry(
        id: 'TEST-HZ-002',
        name: 'Hazard B',
        latitude: 10.0,
        longitude: 10.0,
        coreRadiusMeters: 500.0,
        preWarningRadiusMeters: 1000.0,
        isSynthetic: true,
        dataSource: 'TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION',
      );

      final t0 = DateTime.utc(2026, 9, 20, 12, 0, 0);

      final engine = GeofencingEngine(
        hazardProvider: MultiHazardProvider([hazardA, hazardB]),
        cooldownDuration: const Duration(seconds: 900),
        nowFn: () => t0,
      );

      // Fix is close to Hazard A (400m) and far from Hazard B (>1000km)
      final fix = DeviceLocationFix(
        latitude: latForDistanceMeters(400.0),
        longitude: 0.0,
        accuracy: 20.0,
        speed: 0.0,
        timestamp: t0,
      );

      final events = await engine.evaluateLocation(fix);
      expect(events.length, equals(1));
      expect(events.first.hazardId, equals('TEST-HZ-001'));
      expect(events.first.nextZone, equals(GeofenceZone.core));

      expect(engine.trackingStates['TEST-HZ-001']!.currentZone, equals(GeofenceZone.core));
      expect(engine.trackingStates['TEST-HZ-002']!.currentZone, equals(GeofenceZone.outside));
    });

    test('Empty production provider returns 0 events and leaves tracking empty', () async {
      final engine = GeofencingEngine(
        hazardProvider: EmptyProductionHazardProvider(),
      );

      final fix = DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: 10.0,
        speed: 0.0,
        timestamp: DateTime.now().toUtc(),
      );

      final events = await engine.evaluateLocation(fix);
      expect(events, isEmpty);
      expect(engine.trackingStates, isEmpty);
    });
  });
}
