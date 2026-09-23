import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/di/providers.dart';
import 'package:geoshield_mobile/features/admin/data/admin_repository.dart';
import 'package:geoshield_mobile/features/admin/presentation/admin_dashboard_screen.dart';
import 'package:geoshield_mobile/features/admin/presentation/sachet/admin_create_sachet_alert_screen.dart';
import 'package:geoshield_mobile/features/admin/presentation/sachet/admin_sachet_alerts_screen.dart';
import 'package:geoshield_mobile/features/admin/presentation/sachet/admin_sachet_detail_sheet.dart';
import 'package:geoshield_mobile/features/auth/data/auth_repository.dart';
import 'package:geoshield_mobile/features/risk/data/sachet_alert_model.dart';

void main() {
  final testAlert = SachetAlertModel(
    id: 'ALERT-001',
    identifier: 'NDMA-2026-CYC-0042',
    sender: 'imd_alert@sachet.ndma.gov.in',
    sentAt: DateTime.parse('2026-09-23T10:00:00Z'),
    status: 'Actual',
    msgType: 'Alert',
    category: 'Met',
    event: 'Severe Cyclonic Storm',
    urgency: 'Immediate',
    severity: 'Extreme',
    certainty: 'Observed',
    effectiveAt: DateTime.parse('2026-09-23T10:00:00Z'),
    expiresAt: DateTime.parse('2026-09-24T18:00:00Z'),
    headline: 'Severe Cyclone Approaching Odisha Coast',
    description: 'High wind speeds of 150 km/h with heavy inundation.',
    instruction: 'Evacuate to concrete shelters immediately.',
    areaDesc: 'Puri and Jagatsinghpur districts',
    isSynthetic: false,
  );

  group('Admin Dashboard Navigation', () {
    testWidgets('renders SACHET Alerts action tile under Management Actions', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            adminStatsProvider.overrideWith((ref) async => const AdminStats(
                  totalUsers: 10,
                  touristCount: 7,
                  responderCount: 2,
                  adminCount: 1,
                  totalIncidents: 4,
                  activeIncidents: 1,
                  resolvedIncidents: 3,
                  totalLocationsRecorded: 50,
                  activeSosAlerts: 0,
                )),
          ],
          child: const MaterialApp(home: AdminDashboardScreen()),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('SACHET Alerts'), findsOneWidget);
      expect(
        find.text('Manage NDMA disaster alerts and high-priority overrides'),
        findsOneWidget,
      );
      expect(find.byIcon(Icons.campaign_outlined), findsOneWidget);
    });
  });

  group('AdminSachetAlertsScreen Tests', () {
    testWidgets('renders loading state while alerts are loading', (tester) async {
      final completer = Completer<List<SachetAlertModel>>();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith(() => _MockAdminAuthController('ADMIN')),
            adminActiveSachetAlertsProvider.overrideWith((ref) => completer.future),
          ],
          child: const MaterialApp(home: AdminSachetAlertsScreen()),
        ),
      );

      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Loading active disaster alerts...'), findsOneWidget);

      completer.complete(<SachetAlertModel>[]);
      await tester.pumpAndSettle();
    });

    testWidgets('renders empty state when no active alerts exist', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith(() => _MockAdminAuthController('ADMIN')),
            adminActiveSachetAlertsProvider.overrideWith((ref) async => <SachetAlertModel>[]),
          ],
          child: const MaterialApp(home: AdminSachetAlertsScreen()),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('No Active Disaster Alerts'), findsOneWidget);
      expect(find.text('Broadcast New Alert'), findsOneWidget);
      expect(find.byIcon(Icons.add_alert), findsOneWidget);
    });

    testWidgets('renders error state with retry button on failure', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith(() => _MockAdminAuthController('ADMIN')),
            adminActiveSachetAlertsProvider.overrideWith(
              (ref) => Future.error('Connection timed out'),
            ),
          ],
          child: const MaterialApp(home: AdminSachetAlertsScreen()),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.error_outline), findsOneWidget);
      expect(find.textContaining('Failed to load active disaster alerts.'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('renders active alert card with event, badges, and area', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith(() => _MockAdminAuthController('ADMIN')),
            adminActiveSachetAlertsProvider.overrideWith((ref) async => [testAlert]),
          ],
          child: const MaterialApp(home: AdminSachetAlertsScreen()),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Severe Cyclonic Storm'), findsOneWidget);
      expect(find.text('Severe Cyclone Approaching Odisha Coast'), findsOneWidget);
      expect(find.text('EXTREME'), findsOneWidget);
      expect(find.text('IMMEDIATE'), findsOneWidget);
      expect(find.text('Puri and Jagatsinghpur districts'), findsOneWidget);
      expect(find.text('NDMA-2026-CYC-0042'), findsOneWidget);
    });

    testWidgets('rejects non-admin users with Access Denied', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith(() => _MockAdminAuthController('TOURIST')),
          ],
          child: const MaterialApp(home: AdminSachetAlertsScreen()),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Access Denied'), findsOneWidget);
      expect(
        find.text('Administrator authorization is required to access SACHET disaster alert management.'),
        findsOneWidget,
      );
      expect(find.text('Broadcast Alert'), findsNothing);
    });
  });

  group('AdminSachetDetailSheet Tests', () {
    testWidgets('renders full CAP metadata and instruction', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AdminSachetDetailSheet(
              alert: testAlert,
              onCancelAlert: () {},
            ),
          ),
        ),
      );

      expect(find.text('SACHET Alert Details'), findsOneWidget);
      expect(find.text('NDMA-2026-CYC-0042'), findsOneWidget);
      expect(find.text('Severe Cyclonic Storm'), findsOneWidget);
      expect(find.text('Severity: Extreme'), findsOneWidget);
      expect(find.text('Urgency: Immediate'), findsOneWidget);
      expect(find.text('Certainty: Observed'), findsOneWidget);
      expect(find.text('Evacuate to concrete shelters immediately.'), findsOneWidget);
      expect(find.text('Cancel Broadcast Alert'), findsOneWidget);
    });
  });

  group('AdminCreateSachetAlertScreen Tests', () {
    testWidgets('validates required fields before submitting', (tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(home: AdminCreateSachetAlertScreen()),
        ),
      );

      await tester.pumpAndSettle();

      // Clear the identifier to test validation
      final identifierField = find.widgetWithText(TextFormField, 'Identifier *');
      await tester.enterText(identifierField, '');

      // Tap submit
      final submitButton = find.text('Verify & Submit Alert');
      await tester.tap(submitButton);
      await tester.pumpAndSettle();

      expect(find.text('Identifier is required'), findsOneWidget);
      expect(find.text('Sender is required'), findsOneWidget);
      expect(find.text('Event designation is required'), findsOneWidget);
    });

    testWidgets('shows confirmation dialog with high-priority override warning on valid submit', (tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(home: AdminCreateSachetAlertScreen()),
        ),
      );

      await tester.pumpAndSettle();

      // Enter valid fields
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Sender *'),
        'test_admin@sachet.ndma.gov.in',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Event Designation *'),
        'Severe Tsunami Warning',
      );

      // Tap submit
      await tester.tap(find.text('Verify & Submit Alert'));
      await tester.pumpAndSettle();

      // Verify confirmation dialog
      expect(find.text('Confirm SACHET Broadcast'), findsOneWidget);
      expect(find.textContaining('HIGH-PRIORITY DISASTER OVERRIDE:'), findsOneWidget);
      expect(find.text('Event: Severe Tsunami Warning'), findsOneWidget);
      expect(find.text('Confirm Broadcast'), findsOneWidget);
      expect(find.text('Cancel'), findsOneWidget);
    });
  });
}

class _MockAdminAuthController extends AuthController {
  _MockAdminAuthController(this._role);
  final String _role;

  @override
  Session? build() => Session(role: _role);
}
