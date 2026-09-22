import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/di/providers.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/network/network_exception.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';
import 'package:geoshield_mobile/features/sos/data/sos_outbox_item.dart';
import 'package:geoshield_mobile/features/sos/data/sos_outbox_store.dart';
import 'package:geoshield_mobile/features/sos/data/sos_repository.dart';
import 'package:geoshield_mobile/features/sos/presentation/sos_status_screen.dart';

class _NoSessionStorage implements SecureSessionStorage {
  @override
  Future<String?> readAccessToken() async => null;
  @override
  Future<String?> readRole() async => null;
  @override
  Future<void> save({required String accessToken, required String role}) async {}
  @override
  Future<String?> readBackendBaseUrl() async => null;
  @override
  Future<void> saveBackendBaseUrl(String baseUrl) async {}
  @override
  Future<void> clearBackendBaseUrl() async {}
  @override
  Future<void> clear() async {}
}

class _ThrowingSosRepository extends SosRepository {
  _ThrowingSosRepository(SosOutboxStore store)
      : super(GeoShieldApiClient(_NoSessionStorage()), outboxStore: store);

  @override
  Future<SosAlert?> getActiveSos() async {
    throw const NetworkException('HTTP 503 Service Unavailable');
  }

  @override
  Future<int> drainOutbox({bool ignoreBackoff = false}) async => 0;
}

class _RetryTrackingSosRepository extends SosRepository {
  _RetryTrackingSosRepository(SosOutboxStore store)
      : super(GeoShieldApiClient(_NoSessionStorage()), outboxStore: store);

  String? retriedId;

  @override
  Future<void> retryFailedAlert(String clientRequestId) async {
    retriedId = clientRequestId;
    await outboxStore?.resetFailedToPending(clientRequestId);
  }

  @override
  Future<SosAlert?> getActiveSos() async => null;

  @override
  Future<int> drainOutbox({bool ignoreBackoff = false}) async => 0;
}

void main() {
  Widget createTestWidget(SosAlert alert, {SosRepository? repo, SosOutboxStore? store}) {
    final effectiveStore = store ?? InMemorySosOutboxStore();
    final effectiveRepo = repo ??
        SosRepository(
          GeoShieldApiClient(_NoSessionStorage()),
          outboxStore: effectiveStore,
        );

    return ProviderScope(
      overrides: [
        sosOutboxStoreProvider.overrideWithValue(effectiveStore),
        sosRepositoryProvider.overrideWithValue(effectiveRepo),
      ],
      child: MaterialApp(
        home: SosStatusScreen(initialAlert: alert),
      ),
    );
  }

  Finder findRichText(String text) {
    return find.byWidgetPredicate(
      (widget) => widget is RichText && widget.text.toPlainText().contains(text),
    );
  }

  group('SosStatusScreen Widget Tests', () {
    testWidgets('renders honest Pending Delivery (Offline) banner and guidance when offline',
        (tester) async {
      final offlineAlert = SosAlert(
        sosId: 'local-test-123',
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.pending,
        triggeredAt: DateTime.utc(2026, 9, 21, 12, 0, 0),
        clientRequestId: 'test-req-id',
        isPendingDelivery: true,
      );

      await tester.pumpWidget(createTestWidget(offlineAlert));
      await tester.pumpAndSettle();

      expect(find.text('Pending Delivery (Offline)'), findsOneWidget);
      expect(find.byIcon(Icons.cloud_off_rounded), findsOneWidget);
      expect(findRichText('Queued Locally'), findsOneWidget);
      expect(
        find.textContaining('Your device is currently offline'),
        findsOneWidget,
      );
    });

    testWidgets('renders delivered active SOS with standard guidance and server delivery state',
        (tester) async {
      final onlineAlert = SosAlert(
        sosId: 'server-sos-999',
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.pending,
        triggeredAt: DateTime.utc(2026, 9, 21, 12, 0, 0),
        clientRequestId: 'test-req-id',
        isPendingDelivery: false,
      );

      await tester.pumpWidget(createTestWidget(onlineAlert));
      await tester.pumpAndSettle();

      expect(find.text('Pending — Waiting for responder'), findsOneWidget);
      expect(findRichText('Delivered to GeoShield Server'), findsOneWidget);
      expect(
        find.textContaining('GeoShield emergency responders have received your alert'),
        findsOneWidget,
      );
    });

    testWidgets('renders lifecycle timestamps when populated', (tester) async {
      final detailedAlert = SosAlert(
        sosId: 'server-sos-888',
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.responding,
        triggeredAt: DateTime.utc(2026, 9, 21, 12, 0, 0),
        acknowledgedAt: DateTime.utc(2026, 9, 21, 12, 1, 0),
        respondingAt: DateTime.utc(2026, 9, 21, 12, 2, 0),
        clientRequestId: 'test-req-id',
        isPendingDelivery: false,
      );

      await tester.pumpWidget(createTestWidget(detailedAlert));
      await tester.pumpAndSettle();

      expect(find.text('Responding — Help is on the way'), findsOneWidget);
      expect(findRichText('Acknowledged at:'), findsOneWidget);
      expect(findRichText('Responding at:'), findsOneWidget);
    });

    testWidgets('negative path: 503 on refresh keeps screen mounted and shows warning badge',
        (tester) async {
      final store = InMemorySosOutboxStore();
      final repo = _ThrowingSosRepository(store);
      final alert = SosAlert(
        sosId: 'active-sos-503',
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.acknowledged,
        triggeredAt: DateTime.utc(2026, 9, 21, 12, 0, 0),
        clientRequestId: 'req-503',
        isPendingDelivery: false,
      );

      await tester.pumpWidget(createTestWidget(alert, repo: repo, store: store));
      await tester.pumpAndSettle();

      // Trigger manual refresh via AppBar icon
      final refreshButton = find.byIcon(Icons.refresh);
      expect(refreshButton, findsOneWidget);
      await tester.tap(refreshButton);
      await tester.pumpAndSettle();

      // Screen must still be mounted and show the last known state
      expect(find.text('Emergency SOS'), findsOneWidget);
      expect(find.text('Acknowledged — Responder notified'), findsOneWidget);
      // Warning banner must be visible
      expect(find.text('Unable to refresh status. Showing last known state.'), findsOneWidget);
      expect(find.byIcon(Icons.wifi_off_rounded), findsOneWidget);
    });

    testWidgets('FAILED SOS UI: renders failed banner, 112 button, and Retry Now button',
        (tester) async {
      tester.view.physicalSize = const Size(800, 1200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: 'failed-key-1',
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.utc(2026, 9, 21, 12, 0, 0),
        status: SosOutboxStatus.failed,
        retryCount: 5,
        lastError: 'Maximum retry attempts (5) exhausted.',
      ));

      final repo = _RetryTrackingSosRepository(store);
      final alert = SosAlert(
        sosId: 'local-failed-key-1',
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.pending,
        triggeredAt: DateTime.utc(2026, 9, 21, 12, 0, 0),
        clientRequestId: 'failed-key-1',
        isPendingDelivery: true,
      );

      await tester.pumpWidget(createTestWidget(alert, repo: repo, store: store));
      await tester.pumpAndSettle();

      // Header status banner shows Transmission Failed
      expect(find.text('Transmission Failed'), findsOneWidget);
      expect(find.byIcon(Icons.error_outline_rounded), findsOneWidget);

      // Dedicated transmission failure card
      expect(find.text('Emergency Transmission Failed'), findsOneWidget);
      expect(find.textContaining('Automatic transmission has stopped.'), findsOneWidget);
      expect(find.textContaining('Maximum retry attempts (5) exhausted.'), findsOneWidget);

      // Call 112 button & Retry Now button
      expect(find.widgetWithText(FilledButton, 'Call 112'), findsOneWidget);
      expect(find.widgetWithText(OutlinedButton, 'Retry Now'), findsOneWidget);

      // Delivery state indicates failure
      expect(findRichText('Transmission Failed (Automatic delivery stopped)'), findsOneWidget);

      // Tap Retry Now and verify repository method invoked
      final retryButton = find.widgetWithText(OutlinedButton, 'Retry Now');
      await tester.tap(retryButton);
      await tester.pumpAndSettle();

      expect(repo.retriedId, 'failed-key-1');
    });
  });
}
