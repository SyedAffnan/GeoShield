import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/di/providers.dart';
import 'package:geoshield_mobile/core/geofencing/models/geofence_zone.dart';
import 'package:geoshield_mobile/core/geofencing/presentation/geofence_controller.dart';
import 'package:geoshield_mobile/core/geofencing/presentation/geofence_status_card.dart';

class TestGeofenceController extends GeofenceController {
  TestGeofenceController(this._initialState);

  final GeofenceState _initialState;

  @override
  GeofenceState build() => _initialState;
}

void main() {
  Widget buildTestableWidget(GeofenceState state) {
    return ProviderScope(
      overrides: [
        geofenceControllerProvider.overrideWith(
          () => TestGeofenceController(state),
        ),
      ],
      child: const MaterialApp(
        home: Scaffold(
          body: GeofenceStatusCard(),
        ),
      ),
    );
  }

  group('GeofenceStatusCard Widget Tests', () {
    testWidgets('Renders empty when state is GeofenceInitial or GeofenceUnavailable',
        (tester) async {
      await tester.pumpWidget(buildTestableWidget(const GeofenceInitial()));
      expect(find.byType(Card), findsNothing);

      await tester.pumpWidget(
        buildTestableWidget(const GeofenceUnavailable('Location error')),
      );
      expect(find.byType(Card), findsNothing);
    });

    testWidgets('Renders outside status correctly without Test Fixture badge',
        (tester) async {
      final readyState = GeofenceReady(
        activeZone: GeofenceZone.outside,
        isSynthetic: false,
        evaluatedAt: DateTime.now().toUtc(),
      );

      await tester.pumpWidget(buildTestableWidget(readyState));
      expect(find.byType(Card), findsOneWidget);
      expect(find.text('No active hazard nearby'), findsOneWidget);
      expect(find.text('Autonomous geofence monitoring active'), findsOneWidget);
      expect(find.text('Test Fixture'), findsNothing);
    });

    testWidgets('Renders pre-warning status with Test Fixture badge when synthetic',
        (tester) async {
      final readyState = GeofenceReady(
        activeZone: GeofenceZone.preWarning,
        nearestHazardId: 'TEST-HZ-001',
        nearestHazardName: 'Synthetic Hazard 001',
        nearestDistanceMeters: 750.0,
        isSynthetic: true,
        evaluatedAt: DateTime.now().toUtc(),
      );

      await tester.pumpWidget(buildTestableWidget(readyState));
      expect(find.byType(Card), findsOneWidget);
      expect(find.text('Approaching hazard zone'), findsOneWidget);
      expect(find.textContaining('Synthetic Hazard 001 (750 m)'), findsOneWidget);
      expect(find.text('Test Fixture'), findsOneWidget);
    });

    testWidgets('Renders core status with Test Fixture badge when synthetic',
        (tester) async {
      final readyState = GeofenceReady(
        activeZone: GeofenceZone.core,
        nearestHazardId: 'TEST-HZ-001',
        nearestHazardName: 'Synthetic Hazard 001',
        nearestDistanceMeters: 350.0,
        isSynthetic: true,
        evaluatedAt: DateTime.now().toUtc(),
      );

      await tester.pumpWidget(buildTestableWidget(readyState));
      expect(find.byType(Card), findsOneWidget);
      expect(find.text('Inside designated hazard zone'), findsOneWidget);
      expect(find.textContaining('Synthetic Hazard 001 (350 m)'), findsOneWidget);
      expect(find.text('Test Fixture'), findsOneWidget);
    });
  });
}
