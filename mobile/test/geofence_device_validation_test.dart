import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/di/providers.dart';
import 'package:geoshield_mobile/core/geofencing/models/geofence_zone.dart';
import 'package:geoshield_mobile/core/geofencing/presentation/geofence_controller.dart';
import 'package:geoshield_mobile/core/geofencing/presentation/geofence_status_card.dart';
import 'package:geoshield_mobile/core/geofencing/services/geofence_notification_service.dart';
import 'package:geoshield_mobile/core/geofencing/services/geofencing_engine.dart';
import 'package:geoshield_mobile/core/geofencing/services/hazard_geometry_provider.dart';
import 'package:geoshield_mobile/core/location/device_location_service.dart';

class RecordingNotificationService extends GeofenceNotificationService {
  final List<GeofenceTransitionEvent> handledEvents = [];

  @override
  Future<void> initialize() async {
    // Recorded initialization
  }

  @override
  Future<void> handleTransition(GeofenceTransitionEvent event) async {
    handledEvents.add(event);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Geofence Integration & Device Runtime Validation', () {
    late SyntheticTestHazardProvider hazardProvider;
    late GeofencingEngine engine;
    late RecordingNotificationService notificationService;
    late ProviderContainer container;

    setUp(() {
      hazardProvider = SyntheticTestHazardProvider();
      engine = GeofencingEngine(hazardProvider: hazardProvider);
      notificationService = RecordingNotificationService();

      container = ProviderContainer(
        overrides: [
          hazardGeometryProvider.overrideWithValue(hazardProvider),
          geofencingEngineProvider.overrideWithValue(engine),
          geofenceNotificationServiceProvider
              .overrideWithValue(notificationService),
        ],
      );
    });

    tearDown(() {
      container.dispose();
    });

    test('1. Phase 0.2 GPS Location Validity Enforcement', () {
      final now = DateTime.now().toUtc();

      // Valid fix within thresholds
      final validFix = DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: 25.0,
        speed: 1.5,
        timestamp: now.subtract(const Duration(seconds: 10)),
      );
      expect(GeofencingEngine.isLocationValid(validFix, now), isTrue);

      // Null accuracy allowed per Phase 0.2
      final nullAccFix = DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: null,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 10)),
      );
      expect(GeofencingEngine.isLocationValid(nullAccFix, now), isTrue);

      // Accuracy > 100m rejected
      final highAccFix = DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: 101.0,
        speed: 1.5,
        timestamp: now.subtract(const Duration(seconds: 10)),
      );
      expect(GeofencingEngine.isLocationValid(highAccFix, now), isFalse);

      // Negative accuracy rejected
      final negAccFix = DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: -1.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 10)),
      );
      expect(GeofencingEngine.isLocationValid(negAccFix, now), isFalse);

      // Future timestamp strictly rejected (no future tolerance)
      final futureFix = DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: 10.0,
        speed: null,
        timestamp: now.add(const Duration(seconds: 2)),
      );
      expect(GeofencingEngine.isLocationValid(futureFix, now), isFalse);

      // Fix older than 15 minutes rejected
      final staleFix = DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: 10.0,
        speed: null,
        timestamp: now.subtract(const Duration(minutes: 16)),
      );
      expect(GeofencingEngine.isLocationValid(staleFix, now), isFalse);

      // Out of bounds coordinates rejected
      final invalidLatFix = DeviceLocationFix(
        latitude: 91.0,
        longitude: 77.2090,
        accuracy: 10.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 10)),
      );
      expect(GeofencingEngine.isLocationValid(invalidLatFix, now), isFalse);
    });

    test('2. Full Zone Transition Pipeline: OUTSIDE -> PRE_WARNING -> CORE -> PRE_WARNING -> OUTSIDE', () async {
      final controller = container.read(geofenceControllerProvider.notifier);
      final now = DateTime.now().toUtc();

      // TEST-HZ-001 is at (28.6139, 77.2090)
      // Step A: Far south point (> 1500m away) -> OUTSIDE
      final farFix = DeviceLocationFix(
        latitude: 28.6139 - 0.02,
        longitude: 77.2090,
        accuracy: 80.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 50)),
      );
      await controller.processLocationFix(farFix);
      final GeofenceState s1 = container.read(geofenceControllerProvider);
      expect(s1, isA<GeofenceReady>());
      expect((s1 as GeofenceReady).activeZone, equals(GeofenceZone.outside));
      expect(notificationService.handledEvents, isEmpty);

      // Step B: Move into PRE_WARNING (d <= 1000m).
      // -0.007 deg lat ~ 778m south.
      final preWarningFix = DeviceLocationFix(
        latitude: 28.6139 - 0.007,
        longitude: 77.2090,
        accuracy: 80.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 40)),
      );
      await controller.processLocationFix(preWarningFix);
      final GeofenceState s2 = container.read(geofenceControllerProvider);
      expect(s2, isA<GeofenceReady>());
      final preState = s2 as GeofenceReady;
      expect(preState.activeZone, equals(GeofenceZone.preWarning));
      expect(preState.nearestHazardId, equals('TEST-HZ-001'));
      expect(preState.isSynthetic, isTrue);
      expect(notificationService.handledEvents.length, equals(1));
      expect(notificationService.handledEvents.last.nextZone, equals(GeofenceZone.preWarning));

      // Step C: Move into CORE (d <= 500m).
      // -0.003 deg lat ~ 333m south.
      final coreFix = DeviceLocationFix(
        latitude: 28.6139 - 0.003,
        longitude: 77.2090,
        accuracy: 80.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 30)),
      );
      await controller.processLocationFix(coreFix);
      final GeofenceState s3 = container.read(geofenceControllerProvider);
      expect(s3, isA<GeofenceReady>());
      expect((s3 as GeofenceReady).activeZone, equals(GeofenceZone.core));
      expect(notificationService.handledEvents.length, equals(2));
      expect(notificationService.handledEvents.last.nextZone, equals(GeofenceZone.core));

      // Step D: Move to 550m south. With acc=80m, H=160m, Core exit is 660m. Should REMAIN CORE due to hysteresis!
      final hysteresisCoreFix = DeviceLocationFix(
        latitude: 28.6139 - 0.005,
        longitude: 77.2090,
        accuracy: 80.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 20)),
      );
      await controller.processLocationFix(hysteresisCoreFix);
      final GeofenceState s4 = container.read(geofenceControllerProvider);
      expect(s4, isA<GeofenceReady>());
      expect((s4 as GeofenceReady).activeZone, equals(GeofenceZone.core));

      // Step E: Move beyond Core exit boundary (> 660m) but <= 1160m south. Should EXIT to PRE_WARNING!
      final exitToPreWarningFix = DeviceLocationFix(
        latitude: 28.6139 - 0.007,
        longitude: 77.2090,
        accuracy: 80.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 10)),
      );
      await controller.processLocationFix(exitToPreWarningFix);
      final GeofenceState s5 = container.read(geofenceControllerProvider);
      expect(s5, isA<GeofenceReady>());
      expect((s5 as GeofenceReady).activeZone, equals(GeofenceZone.preWarning));

      // Step F: Move to 1050m south. With acc=80m, H=160m, Pre-warning exit is 1160m. Should REMAIN PRE_WARNING due to hysteresis!
      final hysteresisPreFix = DeviceLocationFix(
        latitude: 28.6139 - 0.0095,
        longitude: 77.2090,
        accuracy: 80.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 5)),
      );
      await controller.processLocationFix(hysteresisPreFix);
      final GeofenceState s6 = container.read(geofenceControllerProvider);
      expect(s6, isA<GeofenceReady>());
      expect((s6 as GeofenceReady).activeZone, equals(GeofenceZone.preWarning));

      // Step G: Move beyond Pre-warning exit boundary (> 1160m south). Should EXIT to OUTSIDE!
      final exitOutsideFix = DeviceLocationFix(
        latitude: 28.6139 - 0.012,
        longitude: 77.2090,
        accuracy: 80.0,
        speed: null,
        timestamp: now,
      );
      await controller.processLocationFix(exitOutsideFix);
      final GeofenceState s7 = container.read(geofenceControllerProvider);
      expect(s7, isA<GeofenceReady>());
      expect((s7 as GeofenceReady).activeZone, equals(GeofenceZone.outside));
    });

    test('3. Cooldown suppresses notifications while state updates unconditionally', () async {
      final controller = container.read(geofenceControllerProvider.notifier);
      final now = DateTime.now().toUtc();
      final t0 = now.subtract(const Duration(minutes: 2));

      // Enter PRE_WARNING at T0 south of TEST-HZ-001 -> first alert emitted
      final fix1 = DeviceLocationFix(
        latitude: 28.6139 - 0.007,
        longitude: 77.2090,
        accuracy: 50.0,
        speed: null,
        timestamp: t0,
      );
      await controller.processLocationFix(fix1);
      expect(notificationService.handledEvents.length, equals(1));
      expect(notificationService.handledEvents.first.cooldownSuppressed, isFalse);

      // Rapidly enter CORE at T0 + 30s (within 900s cooldown)
      final fix2 = DeviceLocationFix(
        latitude: 28.6139 - 0.003,
        longitude: 77.2090,
        accuracy: 50.0,
        speed: null,
        timestamp: t0.add(const Duration(seconds: 30)),
      );
      await controller.processLocationFix(fix2);

      // STATE MACHINE RULE: State updates to CORE unconditionally!
      final GeofenceState state3 = container.read(geofenceControllerProvider);
      expect((state3 as GeofenceReady).activeZone, equals(GeofenceZone.core));

      // But notification transition event records cooldownSuppressed = true
      expect(notificationService.handledEvents.length, equals(2));
      expect(notificationService.handledEvents.last.nextZone, equals(GeofenceZone.core));
      expect(notificationService.handledEvents.last.cooldownSuppressed, isTrue);
    });

    testWidgets('4. GeofenceStatusCard updates reactively from controller state',
        (tester) async {
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            home: Scaffold(
              body: GeofenceStatusCard(),
            ),
          ),
        ),
      );

      // Initial state is GeofenceInitial -> Card not visible
      expect(find.byType(Card), findsNothing);

      // Process a valid CORE fix south of TEST-HZ-001
      final controller = container.read(geofenceControllerProvider.notifier);
      final now = DateTime.now().toUtc();
      await controller.processLocationFix(DeviceLocationFix(
        latitude: 28.6139 - 0.003,
        longitude: 77.2090,
        accuracy: 50.0,
        speed: null,
        timestamp: now.subtract(const Duration(seconds: 5)),
      ));

      await tester.pumpAndSettle();

      // Card must be visible with CORE state and synthetic badge
      expect(find.byType(Card), findsOneWidget);
      expect(find.text('Inside designated hazard zone'), findsOneWidget);
      expect(find.text('Test Fixture'), findsOneWidget);
      expect(find.byIcon(Icons.warning_rounded), findsOneWidget);

      // Process an invalid fix -> Card must disappear
      await controller.processLocationFix(DeviceLocationFix(
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy: 150.0, // Invalid accuracy > 100m
        speed: null,
        timestamp: now,
      ));

      await tester.pumpAndSettle();
      expect(find.byType(Card), findsNothing);
    });

    test('5. Notification Content & Channel Compliance', () {
      final now = DateTime.utc(2026, 9, 20, 10, 0, 0);

      final coreEvent = GeofenceTransitionEvent(
        hazardId: 'TEST-HZ-001',
        hazardName: 'Synthetic High-Risk Curve',
        previousZone: GeofenceZone.preWarning,
        nextZone: GeofenceZone.core,
        distanceMeters: 450.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: true,
        evaluatedAt: now,
      );

      final (coreTitle, coreBody) =
          GeofenceNotificationService.buildNotificationContent(coreEvent);
      expect(coreTitle, equals('[TEST FIXTURE] Safety Alert'));
      expect(coreBody,
          equals('Safety alert: you are inside the designated hazard zone.'));

      final preEvent = GeofenceTransitionEvent(
        hazardId: 'TEST-HZ-001',
        hazardName: 'Synthetic High-Risk Curve',
        previousZone: GeofenceZone.outside,
        nextZone: GeofenceZone.preWarning,
        distanceMeters: 850.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: true,
        evaluatedAt: now,
      );

      final (preTitle, preBody) =
          GeofenceNotificationService.buildNotificationContent(preEvent);
      expect(preTitle, equals('[TEST FIXTURE] Safety Warning'));
      expect(preBody,
          equals('Safety warning: approaching a designated hazard zone.'));

      final outsideEvent = GeofenceTransitionEvent(
        hazardId: 'TEST-HZ-001',
        hazardName: 'Synthetic High-Risk Curve',
        previousZone: GeofenceZone.preWarning,
        nextZone: GeofenceZone.outside,
        distanceMeters: 1250.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: true,
        evaluatedAt: now,
      );

      final (outsideTitle, outsideBody) =
          GeofenceNotificationService.buildNotificationContent(outsideEvent);
      expect(outsideTitle, isEmpty);
      expect(outsideBody, isEmpty);

      // Verify static channel metadata
      expect(GeofenceNotificationService.channelId,
          equals('geoshield_hazard_geofence'));
      expect(GeofenceNotificationService.channelName,
          equals('Safety Hazard Geofence Alerts'));
    });
  });
}
