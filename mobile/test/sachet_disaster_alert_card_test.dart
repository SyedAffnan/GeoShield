import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/risk/data/sachet_alert_model.dart';
import 'package:geoshield_mobile/features/risk/presentation/sachet_disaster_alert_card.dart';

void main() {
  Widget buildTestCard({
    required SachetAlertModel alert,
    required bool overrideActive,
  }) {
    return MaterialApp(
      home: Scaffold(
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: SachetDisasterAlertCard(
            alert: alert,
            overrideActive: overrideActive,
          ),
        ),
      ),
    );
  }

  group('SachetDisasterAlertCard Widget Tests', () {
    testWidgets(
        'Renders civil defense advisory without override or synthetic badge',
        (tester) async {
      final alert = SachetAlertModel(
        id: 'MOD-1',
        identifier: 'SACHET-MOD-001',
        sender: 'imd.gov.in',
        sentAt: DateTime.parse('2026-09-20T10:00:00Z'),
        category: 'Met',
        event: 'Heavy Rainfall Advisory',
        urgency: 'Expected',
        severity: 'Moderate',
        certainty: 'Likely',
        effectiveAt: DateTime.parse('2026-09-20T10:00:00Z'),
        expiresAt: DateTime.parse('2026-09-20T18:00:00Z'),
        headline: 'Heavy rain expected in district',
        instruction: 'Carry rain gear and avoid waterlogged roads.',
        areaDesc: 'District Center',
        isSynthetic: false,
      );

      await tester.pumpWidget(
        buildTestCard(alert: alert, overrideActive: false),
      );

      // Verify Advisory status and no CRITICAL override badge
      expect(find.text('CIVIL DEFENSE ADVISORY'), findsOneWidget);
      expect(find.text('DISASTER EMERGENCY OVERRIDE'), findsNothing);
      expect(find.text('CRITICAL'), findsNothing);

      // Verify event and headline
      expect(find.text('Heavy Rainfall Advisory'), findsOneWidget);
      expect(find.text('Heavy rain expected in district'), findsOneWidget);

      // Verify instruction
      expect(find.text('Carry rain gear and avoid waterlogged roads.'),
          findsOneWidget);

      // Verify indicator badges
      expect(find.text('Severity: MODERATE'), findsOneWidget);
      expect(find.text('Urgency: EXPECTED'), findsOneWidget);
      expect(find.text('Certainty: LIKELY'), findsOneWidget);

      // Verify synthetic watermark is absent
      expect(
        find.text(
            'TEST FIXTURE / SYNTHETIC — NOT AN AUTHENTIC GOVERNMENT ALERT'),
        findsNothing,
      );
    });

    testWidgets(
        'Renders emergency override with CRITICAL badge and synthetic watermark',
        (tester) async {
      final alert = SachetAlertModel(
        id: 'SYNTH-CRIT-1',
        identifier: 'SYNTH-SACHET-001',
        sender: 'ndma.test.gov.in',
        sentAt: DateTime.parse('2026-09-20T12:00:00Z'),
        category: 'Met',
        event: 'Flash Flood Emergency',
        urgency: 'Immediate',
        severity: 'Extreme',
        certainty: 'Observed',
        effectiveAt: DateTime.parse('2026-09-20T12:00:00Z'),
        expiresAt: DateTime.parse('2026-09-20T20:00:00Z'),
        headline: 'Dangerous rapid flash flooding in progress',
        instruction: 'Evacuate low ground immediately to high shelter.',
        areaDesc: 'Sector 5 Valley Area',
        isSynthetic: true,
      );

      await tester.pumpWidget(
        buildTestCard(alert: alert, overrideActive: true),
      );

      // Verify Disaster Emergency Override title and CRITICAL badge
      expect(find.text('DISASTER EMERGENCY OVERRIDE'), findsOneWidget);
      expect(find.text('CIVIL DEFENSE ADVISORY'), findsNothing);
      expect(find.text('CRITICAL'), findsOneWidget);

      // Verify event and instruction
      expect(find.text('Flash Flood Emergency'), findsOneWidget);
      expect(
        find.text('Evacuate low ground immediately to high shelter.'),
        findsOneWidget,
      );

      // Verify indicator badges
      expect(find.text('Severity: EXTREME'), findsOneWidget);
      expect(find.text('Urgency: IMMEDIATE'), findsOneWidget);
      expect(find.text('Certainty: OBSERVED'), findsOneWidget);

      // Verify synthetic watermark is present
      expect(
        find.text(
            'TEST FIXTURE / SYNTHETIC — NOT AN AUTHENTIC GOVERNMENT ALERT'),
        findsOneWidget,
      );
    });
  });
}
