import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/risk/data/risk_repository.dart';
import 'package:geoshield_mobile/features/risk/presentation/risk_dashboard_screen.dart';

void main() {
  group('RiskFactorDetailModel JSON contract tests', () {
    test('fromJson correctly parses backend-shaped JSON payload with normalizedValue and reason', () {
      final backendJson = <String, dynamic>{
        'factorName': 'WEATHER',
        'factor': 'WEATHER',
        'weight': 0.20,
        'available': true,
        'rawValue': 'Rainy, WMO 61',
        'normalizedValue': 79.07,
        'weightedContribution': 15.81,
        'reason': null,
        'explanation': 'Rainy weather conditions',
        'source': 'Open-Meteo & MoRTH 2024 Table 3.8',
        'sourceType': 'EXTERNAL_API',
        'sourceIdentifier': 'Open-Meteo-WMO-61',
        'observedAt': '2026-08-24T10:00:00Z',
        'freshnessSeconds': 120,
        'geographicScope': 'LOCATION_GRID_0.01DEG[12.97,77.59]',
        'normalizationDetails': 'Rescaled relative to published MoRTH severity',
      };

      final detail = RiskFactorDetailModel.fromJson(backendJson);

      expect(detail.factor, 'WEATHER');
      expect(detail.weight, 0.20);
      expect(detail.available, isTrue);
      expect(detail.rawValue, 'Rainy, WMO 61');
      expect(detail.normalizedValue, 79.07);
      expect(detail.weightedContribution, 15.81);
      expect(detail.reason, isNull);
      expect(detail.explanation, 'Rainy weather conditions');
      expect(detail.source, 'Open-Meteo & MoRTH 2024 Table 3.8');
      expect(detail.sourceType, 'EXTERNAL_API');
      expect(detail.sourceIdentifier, 'Open-Meteo-WMO-61');
      expect(detail.observedAt, DateTime.parse('2026-08-24T10:00:00Z'));
      expect(detail.freshnessSeconds, 120);
      expect(detail.geographicScope, 'LOCATION_GRID_0.01DEG[12.97,77.59]');
      expect(detail.normalizationDetails, 'Rescaled relative to published MoRTH severity');
    });

    test('fromJson correctly parses unavailable factor with null normalizedValue and backend reason string', () {
      final backendJson = <String, dynamic>{
        'factorName': 'SERVICE_PROXIMITY',
        'weight': 0.15,
        'available': false,
        'rawValue': null,
        'normalizedValue': null,
        'weightedContribution': 0.0,
        'reason': 'Service directory offline',
        'explanation': 'Emergency service lookup unavailable',
        'source': 'EmergencyServicesService',
      };

      final detail = RiskFactorDetailModel.fromJson(backendJson);

      expect(detail.factor, 'SERVICE_PROXIMITY');
      expect(detail.weight, 0.15);
      expect(detail.available, isFalse);
      expect(detail.rawValue, isNull);
      expect(detail.normalizedValue, isNull);
      expect(detail.weightedContribution, 0.0);
      expect(detail.reason, 'Service directory offline');
      expect(detail.explanation, 'Emergency service lookup unavailable');
      expect(detail.source, 'EmergencyServicesService');
      expect(detail.sourceType, isNull);
      expect(detail.sourceIdentifier, isNull);
      expect(detail.observedAt, isNull);
      expect(detail.freshnessSeconds, isNull);
      expect(detail.geographicScope, isNull);
      expect(detail.normalizationDetails, isNull);
    });
  });

  group('RiskFactorCard UI reason path widget tests', () {
    testWidgets('Renders unavailable factor and displays backend reason string in UI', (tester) async {
      const factor = RiskFactor(
        factor: 'SERVICE_PROXIMITY',
        available: false,
        normalizedRisk: null,
        contribution: 0.0,
        explanation: 'Nearest hospital or police facility could not be determined.',
      );

      const detail = RiskFactorDetailModel(
        factor: 'SERVICE_PROXIMITY',
        weight: 0.15,
        available: false,
        rawValue: null,
        normalizedValue: null,
        weightedContribution: 0.0,
        reason: 'Service directory offline',
        explanation: 'Nearest hospital or police facility could not be determined.',
        source: 'OpenStreetMap Emergency Directory',
        sourceType: 'FACILITY_REGISTRY',
        sourceIdentifier: null,
        observedAt: null,
        freshnessSeconds: null,
        geographicScope: 'COORDINATES_UNAVAILABLE',
        normalizationDetails: 'Requires emergency service reference dataset',
      );

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Padding(
              padding: EdgeInsets.all(16),
              child: RiskFactorCard(
                factor: factor,
                detail: detail,
              ),
            ),
          ),
        ),
      );

      // Verify header and availability status
      expect(find.text('Service Proximity'), findsOneWidget);
      expect(find.text('Weight 15%'), findsOneWidget);
      expect(find.text('UNAVAILABLE'), findsOneWidget);

      // Verify the rendered Reason branch receives and displays the backend reason value
      expect(find.text('Reason: Service directory offline'), findsOneWidget);
      expect(find.text('Source: OpenStreetMap Emergency Directory'), findsOneWidget);
      expect(find.text('Scope: COORDINATES_UNAVAILABLE'), findsOneWidget);
      expect(
        find.text('Nearest hospital or police facility could not be determined.'),
        findsOneWidget,
      );
    });

    testWidgets('Renders available factor with observed value, provenance, and without reason text', (tester) async {
      const factor = RiskFactor(
        factor: 'WEATHER',
        available: true,
        normalizedRisk: 79.07,
        contribution: 15.81,
        explanation: 'Rainy conditions detected.',
      );

      final detail = RiskFactorDetailModel(
        factor: 'WEATHER',
        weight: 0.20,
        available: true,
        rawValue: 'Rainy (WMO 61)',
        normalizedValue: 79.07,
        weightedContribution: 15.81,
        reason: null,
        explanation: 'Rainy conditions detected.',
        source: 'Open-Meteo API',
        sourceType: 'EXTERNAL_API',
        sourceIdentifier: 'Open-Meteo-WMO-61',
        observedAt: DateTime.parse('2026-08-24T10:00:00Z'),
        freshnessSeconds: 45,
        geographicScope: 'LOCATION_GRID_0.01DEG[12.97,77.59]',
        normalizationDetails: 'Linear mapping from Table 3.8',
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Padding(
              padding: const EdgeInsets.all(16),
              child: RiskFactorCard(
                factor: factor,
                detail: detail,
              ),
            ),
          ),
        ),
      );

      expect(find.text('Weather'), findsOneWidget);
      expect(find.text('Weight 20%'), findsOneWidget);
      expect(find.text('AVAILABLE'), findsOneWidget);
      expect(find.text('Observed: Rainy (WMO 61)'), findsOneWidget);
      expect(find.text('Risk: 79.07'), findsOneWidget);
      expect(find.text('Contribution: 15.81 pts'), findsOneWidget);
      expect(find.text('Source: Open-Meteo API'), findsOneWidget);
      expect(find.text('Source ID: Open-Meteo-WMO-61'), findsOneWidget);
      expect(find.text('Scope: LOCATION_GRID_0.01DEG[12.97,77.59]'), findsOneWidget);
      expect(find.text('Recorded: 2026-08-24T10:00:00.000Z (45s ago)'), findsOneWidget);
      expect(find.textContaining('Reason:'), findsNothing);
    });
  });
}
