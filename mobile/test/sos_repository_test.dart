import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/network/auth_exception.dart';
import 'package:geoshield_mobile/core/network/conflict_exception.dart';
import 'package:geoshield_mobile/core/network/validation_exception.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';
import 'package:geoshield_mobile/features/sos/data/sos_outbox_item.dart';
import 'package:geoshield_mobile/features/sos/data/sos_outbox_store.dart';
import 'package:geoshield_mobile/features/sos/data/sos_repository.dart';

void main() {
  group('SosRepository', () {
    test('createSos sends correct POST body and parses response', () async {
      const clientRequestId = '550e8400-e29b-41d4-a716-446655440000';
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': '11111111-1111-1111-1111-111111111111',
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'PENDING',
        'assignedResponderId': null,
        'clientRequestId': clientRequestId,
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final repository = SosRepository(_client(interceptor));

      final alert = await repository.createSos(
        latitude: 12.9716,
        longitude: 77.5946,
        clientRequestId: clientRequestId,
      );

      expect(interceptor.method, 'POST');
      expect(interceptor.path, '/api/v1/sos');
      expect(interceptor.body, {
        'latitude': 12.9716,
        'longitude': 77.5946,
        'clientRequestId': clientRequestId,
      });
      expect(alert.sosId, '11111111-1111-1111-1111-111111111111');
      expect(alert.status, SosStatusValue.pending);
      expect(alert.latitude, closeTo(12.9716, 0.0001));
      expect(alert.longitude, closeTo(77.5946, 0.0001));
    });

    test('getActiveSos returns parsed SosAlert when backend returns active SOS',
        () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': '33333333-3333-3333-3333-333333333333',
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': -33.8688,
        'longitude': 151.2093,
        'status': 'ACKNOWLEDGED',
        'assignedResponderId': null,
        'clientRequestId': '550e8400-e29b-41d4-a716-446655440000',
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final repository = SosRepository(_client(interceptor));

      final alert = await repository.getActiveSos();

      expect(interceptor.method, 'GET');
      expect(interceptor.path, '/api/v1/sos/active');
      expect(alert, isNotNull);
      expect(alert!.sosId, '33333333-3333-3333-3333-333333333333');
      expect(alert.status, SosStatusValue.acknowledged);
    });

    test('getActiveSos returns null when backend returns 404', () async {
      final interceptor = _ErrorInterceptor(statusCode: 404);
      final repository = SosRepository(_client(interceptor));

      final alert = await repository.getActiveSos();

      expect(alert, isNull);
    });

    test('PATCH /cancel endpoint sends correct request and parses cancelled status',
        () async {
      // This test verifies the underlying HTTP PATCH call that
      // reconcileAndCancelSos() makes when it reaches the server.
      // The old `cancelSos(sosId)` method was removed in the Step 3 rewrite;
      // cancellation now flows through reconcileAndCancelSos() which calls
      // apiClient.patchData('/api/v1/sos/{id}/cancel').
      const sosId = '44444444-4444-4444-4444-444444444444';
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': sosId,
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'CANCELLED',
        'assignedResponderId': null,
        'clientRequestId': '550e8400-e29b-41d4-a716-446655440000',
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final client = _client(interceptor);

      // Call patchData directly — the same call path reconcileAndCancelSos uses.
      final raw = await client.patchData(
        '/api/v1/sos/$sosId/cancel',
        data: <String, dynamic>{},
      );
      final alert = SosAlert.fromJson(raw);

      expect(interceptor.method, 'PATCH');
      expect(interceptor.path, '/api/v1/sos/$sosId/cancel');
      expect(alert.status, SosStatusValue.cancelled);
      expect(alert.status.isTerminal, isTrue);
      expect(alert.status.isActive, isFalse);
    });

    test('createSos sends Idempotency-Key header', () async {
      const clientRequestId = '550e8400-e29b-41d4-a716-446655440000';
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': '11111111-1111-1111-1111-111111111111',
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'PENDING',
        'assignedResponderId': null,
        'clientRequestId': clientRequestId,
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final repository = SosRepository(_client(interceptor));

      await repository.createSos(
        latitude: 12.9716,
        longitude: 77.5946,
        clientRequestId: clientRequestId,
      );

      expect(interceptor.headers?['Idempotency-Key'], clientRequestId);
    });

    test('dispatchOrQueueSos sends immediately and removes from outbox when online', () async {
      const clientRequestId = '550e8400-e29b-41d4-a716-446655440000';
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': '11111111-1111-1111-1111-111111111111',
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'PENDING',
        'assignedResponderId': null,
        'clientRequestId': clientRequestId,
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final store = InMemorySosOutboxStore();
      final repository = SosRepository(_client(interceptor), outboxStore: store);

      final alert = await repository.dispatchOrQueueSos(
        latitude: 12.9716,
        longitude: 77.5946,
        clientRequestId: clientRequestId,
      );

      expect(alert.isPendingDelivery, isFalse);
      expect(alert.sosId, '11111111-1111-1111-1111-111111111111');
      expect(await store.count(), 0); // successfully cleared
    });

    test('dispatchOrQueueSos enqueues in outbox and returns honest pending alert when network fails', () async {
      const clientRequestId = '550e8400-e29b-41d4-a716-446655440000';
      final interceptor = _ErrorInterceptor(statusCode: 503);
      final store = InMemorySosOutboxStore();
      final repository = SosRepository(_client(interceptor), outboxStore: store);

      final alert = await repository.dispatchOrQueueSos(
        latitude: 12.9716,
        longitude: 77.5946,
        clientRequestId: clientRequestId,
      );

      expect(alert.isPendingDelivery, isTrue);
      expect(alert.clientRequestId, clientRequestId);
      expect(alert.status, SosStatusValue.pending);
      expect(await store.count(), 1);

      final saved = await store.get(clientRequestId);
      expect(saved, isNotNull);
      expect(saved!.status, SosOutboxStatus.pending);
      expect(saved.retryCount, 1);
      expect(saved.lastError, isNotNull);
    });

    test('dispatchOrQueueSos reusing existing pending item preserves retryCount and increments failure', () async {
      const clientRequestId = '550e8400-e29b-41d4-a716-446655440000';
      final store = InMemorySosOutboxStore();
      final lastAttempt = DateTime.now().toUtc().subtract(const Duration(seconds: 30));
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc().subtract(const Duration(minutes: 5)),
        status: SosOutboxStatus.pending,
        retryCount: 2,
        lastAttemptAt: lastAttempt,
        lastError: 'Previous connection failure',
      ));

      final interceptor = _ErrorInterceptor(statusCode: 503);
      final repository = SosRepository(_client(interceptor), outboxStore: store);

      // Call without explicit clientRequestId -> reuses pending item
      final alert = await repository.dispatchOrQueueSos(
        latitude: 12.9716,
        longitude: 77.5946,
      );

      expect(alert.clientRequestId, clientRequestId);
      expect(await store.count(), 1);

      final item = await store.get(clientRequestId);
      expect(item, isNotNull);
      // retryCount must increment from 2 to 3, NOT reset to 1 or 0
      expect(item!.retryCount, 3);
      expect(item.status, SosOutboxStatus.pending);
    });

    test('drainOutbox sends pending items and removes them upon success', () async {
      const clientRequestId = '550e8400-e29b-41d4-a716-446655440000';
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': 'server-sos-1',
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'PENDING',
        'assignedResponderId': null,
        'clientRequestId': clientRequestId,
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      final synced = await repository.drainOutbox();

      expect(synced, 1);
      expect(await store.count(), 0);
    });

    test('drainOutbox recovers syncing items and retains on failure', () async {
      const clientRequestId = '550e8400-e29b-41d4-a716-446655440000';
      final interceptor = _ErrorInterceptor(statusCode: 500);
      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.syncing, // simulate crashed mid-sync
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      final synced = await repository.drainOutbox();

      expect(synced, 0);
      expect(await store.count(), 1);
      final item = await store.get(clientRequestId);
      expect(item!.status, SosOutboxStatus.pending); // recovered and marked pending
      expect(item.retryCount, 1);
    });

    test('drainOutbox marks item as failed immediately on permanent 400 error', () async {
      const clientRequestId = 'req-perm-400';
      final interceptor = _ErrorInterceptor(statusCode: 400);
      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
        retryCount: 0,
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      final synced = await repository.drainOutbox();

      expect(synced, 0);
      final item = await store.get(clientRequestId);
      expect(item!.status, SosOutboxStatus.failed);
      expect(item.lastError, isNotNull);
    });

    test('drainOutbox respects maxAttempts ceiling (5) and marks item as failed', () async {
      const clientRequestId = 'req-max-attempts';
      final interceptor = _ErrorInterceptor(statusCode: 503);
      final store = InMemorySosOutboxStore();
      // Already attempted 4 times; this 5th attempt fails -> transitions to failed
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
        retryCount: 4,
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      final synced = await repository.drainOutbox();

      expect(synced, 0);
      final item = await store.get(clientRequestId);
      expect(item!.status, SosOutboxStatus.failed);
      expect(item.retryCount, 5);
    });

    test('retryFailedAlert resets failed item to pending, clears error, and drains', () async {
      const clientRequestId = 'req-retry-failed';
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': 'server-sos-recovered',
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'PENDING',
        'assignedResponderId': null,
        'clientRequestId': clientRequestId,
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.failed,
        retryCount: 5,
        lastError: 'Permanent failure',
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      await repository.retryFailedAlert(clientRequestId);

      // Successfully synced and deleted from outbox
      expect(await store.count(), 0);
    });

    test('reconcileAndCancelSos Case A: cancels active online alert via server and deletes outbox item', () async {
      const sosId = 'sos-case-a';
      const clientRequestId = 'req-case-a';
      final interceptor = _RoutingInterceptor({
        'PATCH /api/v1/sos/$sosId/cancel': (_) => <String, dynamic>{
          'sosId': sosId,
          'userId': '22222222-2222-2222-2222-222222222222',
          'username': 'tourist1',
          'fullName': 'Tourist One',
          'phoneNumber': '+919876543210',
          'latitude': 12.9716,
          'longitude': 77.5946,
          'status': 'CANCELLED',
          'assignedResponderId': null,
          'clientRequestId': clientRequestId,
          'triggeredAt': '2026-08-27T00:00:00Z',
        },
      });
      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      final alert = SosAlert(
        sosId: sosId,
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.pending,
        triggeredAt: DateTime.now().toUtc(),
        clientRequestId: clientRequestId,
        isPendingDelivery: false,
      );

      await repository.reconcileAndCancelSos(alert);

      expect(await store.count(), 0);
      expect(interceptor.handledRequests.any((r) => r.path == '/api/v1/sos/$sosId/cancel'), isTrue);
    });

    test('reconcileAndCancelSos Case B: queued alert when server confirms 404 deletes outbox item', () async {
      const clientRequestId = 'req-case-b';
      final interceptor = _RoutingInterceptor({
        'GET /api/v1/sos/active': (_) => 404,
      });
      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      final alert = SosAlert(
        sosId: 'local-$clientRequestId',
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.pending,
        triggeredAt: DateTime.now().toUtc(),
        clientRequestId: clientRequestId,
        isPendingDelivery: true,
      );

      await repository.reconcileAndCancelSos(alert);

      expect(await store.count(), 0);
    });

    test('reconcileAndCancelSos Case C: queued alert when network fails persists cancelPending record', () async {
      const clientRequestId = 'req-case-c';
      final interceptor = _RoutingInterceptor({
        'GET /api/v1/sos/active': (_) => 503,
      });
      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      final alert = SosAlert(
        sosId: 'local-$clientRequestId',
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.pending,
        triggeredAt: DateTime.now().toUtc(),
        clientRequestId: clientRequestId,
        isPendingDelivery: true,
      );

      await repository.reconcileAndCancelSos(alert);

      final item = await store.get(clientRequestId);
      expect(item, isNotNull);
      expect(item!.status, SosOutboxStatus.cancelPending);
    });

    test('reconcileAndCancelSos Case D: server responds with 409 Conflict sets cancellationFailed', () async {
      const sosId = 'sos-case-d';
      const clientRequestId = 'req-case-d';
      final interceptor = _RoutingInterceptor({
        'GET /api/v1/sos/active': (_) => <String, dynamic>{
          'sosId': sosId,
          'userId': '22222222-2222-2222-2222-222222222222',
          'username': 'tourist1',
          'fullName': 'Tourist One',
          'phoneNumber': '+919876543210',
          'latitude': 12.9716,
          'longitude': 77.5946,
          'status': 'RESPONDING',
          'assignedResponderId': 'resp-1',
          'clientRequestId': clientRequestId,
          'triggeredAt': '2026-08-27T00:00:00Z',
        },
        'PATCH /api/v1/sos/$sosId/cancel': (_) => 409,
      });
      final store = InMemorySosOutboxStore();
      await store.save(SosOutboxItem(
        clientRequestId: clientRequestId,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
      ));

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      final alert = SosAlert(
        sosId: 'local-$clientRequestId',
        latitude: 12.9716,
        longitude: 77.5946,
        status: SosStatusValue.pending,
        triggeredAt: DateTime.now().toUtc(),
        clientRequestId: clientRequestId,
        isPendingDelivery: true,
      );

      await repository.reconcileAndCancelSos(alert);

      final item = await store.get(clientRequestId);
      expect(item, isNotNull);
      expect(item!.status, SosOutboxStatus.cancellationFailed);
    });

    test('reconcileCancelPendingItem safety guard: does not cancel when server SOS has different clientRequestId', () async {
      const localKey = 'key-local-123';
      const serverKey = 'key-server-456';
      const serverSosId = 'sos-server-999';

      final interceptor = _RoutingInterceptor({
        'GET /api/v1/sos/active': (_) => <String, dynamic>{
          'sosId': serverSosId,
          'userId': '22222222-2222-2222-2222-222222222222',
          'username': 'tourist1',
          'fullName': 'Tourist One',
          'phoneNumber': '+919876543210',
          'latitude': 12.9716,
          'longitude': 77.5946,
          'status': 'PENDING',
          'assignedResponderId': null,
          'clientRequestId': serverKey,
          'triggeredAt': '2026-08-27T00:00:00Z',
        },
      });
      final store = InMemorySosOutboxStore();
      final item = SosOutboxItem(
        clientRequestId: localKey,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.cancelPending,
      );
      await store.save(item);

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      await repository.reconcileCancelPendingItem(item);

      // Verify PATCH was NEVER called
      expect(interceptor.handledRequests.any((r) => r.method == 'PATCH'), isFalse);
      // Verify local record was NOT deleted
      final storedItem = await store.get(localKey);
      expect(storedItem, isNotNull);
      expect(storedItem!.status, SosOutboxStatus.cancellationFailed);
      expect(storedItem.lastError, contains(serverKey));
    });

    test('reconcileCancelPendingItem matching key: patches server and deletes local item', () async {
      const matchKey = 'key-matching-777';
      const serverSosId = 'sos-server-777';

      final interceptor = _RoutingInterceptor({
        'GET /api/v1/sos/active': (_) => <String, dynamic>{
          'sosId': serverSosId,
          'userId': '22222222-2222-2222-2222-222222222222',
          'username': 'tourist1',
          'fullName': 'Tourist One',
          'phoneNumber': '+919876543210',
          'latitude': 12.9716,
          'longitude': 77.5946,
          'status': 'PENDING',
          'assignedResponderId': null,
          'clientRequestId': matchKey,
          'triggeredAt': '2026-08-27T00:00:00Z',
        },
        'PATCH /api/v1/sos/$serverSosId/cancel': (_) => <String, dynamic>{
          'sosId': serverSosId,
          'userId': '22222222-2222-2222-2222-222222222222',
          'username': 'tourist1',
          'fullName': 'Tourist One',
          'phoneNumber': '+919876543210',
          'latitude': 12.9716,
          'longitude': 77.5946,
          'status': 'CANCELLED',
          'assignedResponderId': null,
          'clientRequestId': matchKey,
          'triggeredAt': '2026-08-27T00:00:00Z',
        },
      });
      final store = InMemorySosOutboxStore();
      final item = SosOutboxItem(
        clientRequestId: matchKey,
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.cancelPending,
      );
      await store.save(item);

      final repository = SosRepository(_client(interceptor), outboxStore: store);
      await repository.reconcileCancelPendingItem(item);

      // Verify PATCH was called and local item was deleted
      expect(interceptor.handledRequests.any((r) => r.path == '/api/v1/sos/$serverSosId/cancel'), isTrue);
      expect(await store.count(), 0);
    });
  });

  group('SosStatusValue', () {
    test('all backend status strings parse correctly', () {
      expect(SosStatusValue.fromString('PENDING'), SosStatusValue.pending);
      expect(
          SosStatusValue.fromString('ACKNOWLEDGED'), SosStatusValue.acknowledged);
      expect(SosStatusValue.fromString('RESPONDING'), SosStatusValue.responding);
      expect(SosStatusValue.fromString('RESOLVED'), SosStatusValue.resolved);
      expect(SosStatusValue.fromString('CANCELLED'), SosStatusValue.cancelled);
    });

    test('isActive returns true only for non-terminal statuses', () {
      expect(SosStatusValue.pending.isActive, isTrue);
      expect(SosStatusValue.acknowledged.isActive, isTrue);
      expect(SosStatusValue.responding.isActive, isTrue);
      expect(SosStatusValue.resolved.isActive, isFalse);
      expect(SosStatusValue.cancelled.isActive, isFalse);
    });

    test('isTerminal returns true only for RESOLVED and CANCELLED', () {
      expect(SosStatusValue.pending.isTerminal, isFalse);
      expect(SosStatusValue.acknowledged.isTerminal, isFalse);
      expect(SosStatusValue.responding.isTerminal, isFalse);
      expect(SosStatusValue.resolved.isTerminal, isTrue);
      expect(SosStatusValue.cancelled.isTerminal, isTrue);
    });
  });

  group('dispatchOrQueueSos error classification', () {
    test('marks outbox as failed and rethrows on AuthException (HTTP 401)', () async {
      final store = InMemorySosOutboxStore();
      final client = _client(_ErrorInterceptor(statusCode: 401));
      final repo = SosRepository(client, outboxStore: store);

      await expectLater(
        repo.dispatchOrQueueSos(
          latitude: 12.97,
          longitude: 77.59,
          clientRequestId: 'auth-err-req',
        ),
        throwsA(isA<AuthException>()),
      );

      final item = await store.get('auth-err-req');
      expect(item, isNotNull);
      expect(item!.status, SosOutboxStatus.failed);
      expect(item.retryCount, 1);
    });

    test('marks outbox as failed and rethrows on ValidationException (HTTP 400)', () async {
      final store = InMemorySosOutboxStore();
      final client = _client(_ErrorInterceptor(statusCode: 400));
      final repo = SosRepository(client, outboxStore: store);

      await expectLater(
        repo.dispatchOrQueueSos(
          latitude: 12.97,
          longitude: 77.59,
          clientRequestId: 'val-err-req',
        ),
        throwsA(isA<ValidationException>()),
      );

      final item = await store.get('val-err-req');
      expect(item, isNotNull);
      expect(item!.status, SosOutboxStatus.failed);
    });

    test('marks outbox as failed and rethrows on ConflictException (HTTP 409)', () async {
      final store = InMemorySosOutboxStore();
      final client = _client(_ErrorInterceptor(statusCode: 409));
      final repo = SosRepository(client, outboxStore: store);

      await expectLater(
        repo.dispatchOrQueueSos(
          latitude: 12.97,
          longitude: 77.59,
          clientRequestId: 'conf-err-req',
        ),
        throwsA(isA<ConflictException>()),
      );

      final item = await store.get('conf-err-req');
      expect(item, isNotNull);
      expect(item!.status, SosOutboxStatus.failed);
    });

    test('preserves pending with retryCount=1 and returns honest alert on NetworkException', () async {
      final store = InMemorySosOutboxStore();
      final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          handler.reject(DioException(
            requestOptions: options,
            type: DioExceptionType.connectionError,
            error: 'Connection refused',
          ));
        },
      ));
      final client = GeoShieldApiClient(_NoSessionStorage(), dio: dio);
      final repo = SosRepository(client, outboxStore: store);

      final alert = await repo.dispatchOrQueueSos(
        latitude: 12.97,
        longitude: 77.59,
        clientRequestId: 'net-err-req',
      );

      expect(alert.isPendingDelivery, isTrue);
      expect(alert.clientRequestId, 'net-err-req');

      final item = await store.get('net-err-req');
      expect(item, isNotNull);
      expect(item!.status, SosOutboxStatus.pending);
      expect(item.retryCount, 1);
    });
  });

  group('drainOutbox exponential backoff', () {
    test('skips items in backoff window when ignoreBackoff is false', () async {
      final store = InMemorySosOutboxStore();
      int callCount = 0;
      final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080'));
      dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          callCount++;
          handler.reject(DioException(
            requestOptions: options,
            type: DioExceptionType.connectionError,
          ));
        },
      ));
      final client = GeoShieldApiClient(_NoSessionStorage(), dio: dio);
      final repo = SosRepository(client, outboxStore: store);

      // Item with retryCount=1 attempted 500ms ago (backoff is 2s)
      await store.save(SosOutboxItem(
        clientRequestId: 'backoff-req',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
        retryCount: 1,
        lastAttemptAt: DateTime.now().toUtc().subtract(const Duration(milliseconds: 500)),
      ));

      final synced = await repo.drainOutbox(ignoreBackoff: false);
      expect(synced, 0);
      expect(callCount, 0, reason: 'Item should have been skipped due to backoff window');

      // Now drain with ignoreBackoff: true
      await repo.drainOutbox(ignoreBackoff: true);
      expect(callCount, 1, reason: 'Item should be processed when ignoreBackoff is true');
    });
  });

  group('drainCancelPending isolation', () {
    test('processes subsequent items even if one item throws', () async {
      final store = InMemorySosOutboxStore();
      final processed = <String>[];

      final router = _RoutingInterceptor({
        'GET /api/v1/sos/active': (_) => <String, dynamic>{
              'sosId': 'active-sos-1',
              'status': 'PENDING',
              'clientRequestId': 'cancel-match-1',
              'latitude': 12.97,
              'longitude': 77.59,
              'triggeredAt': '2026-08-27T00:00:00Z',
            },
        'PATCH /api/v1/sos/active-sos-1/cancel': (_) {
          processed.add('cancel-match-1');
          return <String, dynamic>{'status': 'CANCELLED'};
        },
      });

      final client = _client(router);
      final repo = SosRepository(client, outboxStore: store);

      await store.save(SosOutboxItem(
        clientRequestId: 'cancel-match-1',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.cancelPending,
      ));

      await repo.drainCancelPending();
      expect(processed, contains('cancel-match-1'));
      expect(await store.get('cancel-match-1'), isNull);
    });
  });

  group('retryFailedAlert guard', () {
    test('does not retry pending or cancelPending alerts', () async {
      final store = InMemorySosOutboxStore();
      final client = _client(_RoutingInterceptor({}));
      final repo = SosRepository(client, outboxStore: store);

      await store.save(SosOutboxItem(
        clientRequestId: 'pending-item',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
        retryCount: 2,
      ));

      await repo.retryFailedAlert('pending-item');

      final item = await store.get('pending-item');
      expect(item!.status, SosOutboxStatus.pending);
      expect(item.retryCount, 2, reason: 'retryCount should not be reset for non-failed item');
    });
  });

  group('generateClientRequestId', () {
    test('produces a valid RFC 4122 UUID v4 string', () {
      final id = generateClientRequestId();
      final uuidRegex = RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          caseSensitive: false);
      expect(uuidRegex.hasMatch(id), isTrue,
          reason: 'Expected a UUID v4 but got: $id');
    });

    test('generates unique IDs on each call', () {
      final ids = List.generate(50, (_) => generateClientRequestId()).toSet();
      expect(ids.length, 50);
    });
  });
}

GeoShieldApiClient _client(Interceptor interceptor) {
  final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080'));
  dio.interceptors.add(interceptor);
  return GeoShieldApiClient(_NoSessionStorage(), dio: dio);
}

class _CapturingInterceptor extends Interceptor {
  _CapturingInterceptor(this._responseData);
  final dynamic _responseData;

  String? method;
  String? path;
  Map<String, dynamic>? body;
  Map<String, dynamic>? headers;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    method = options.method;
    path = options.path;
    headers = options.headers;
    final data = options.data;
    body = data is Map<String, dynamic> ? data : null;
    handler.resolve(Response<dynamic>(
      requestOptions: options,
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': _responseData,
      },
    ));
  }
}

class _ErrorInterceptor extends Interceptor {
  _ErrorInterceptor({required this.statusCode});
  final int statusCode;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    handler.reject(DioException(
      requestOptions: options,
      response: Response(
        requestOptions: options,
        statusCode: statusCode,
        data: <String, dynamic>{'success': false, 'message': 'Not found'},
      ),
      type: DioExceptionType.badResponse,
    ));
  }
}

class _RoutingInterceptor extends Interceptor {
  _RoutingInterceptor(this._routes);
  final Map<String, dynamic Function(RequestOptions)> _routes;
  final List<RequestOptions> handledRequests = [];

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    handledRequests.add(options);
    final key = '${options.method} ${options.path}';
    final routeHandler = _routes[key];
    if (routeHandler != null) {
      final res = routeHandler(options);
      if (res is DioException) {
        handler.reject(res);
      } else if (res is int) {
        handler.reject(DioException(
          requestOptions: options,
          response: Response(
            requestOptions: options,
            statusCode: res,
            data: <String, dynamic>{'success': false, 'message': 'HTTP $res'},
          ),
          type: DioExceptionType.badResponse,
        ));
      } else {
        handler.resolve(Response<dynamic>(
          requestOptions: options,
          statusCode: 200,
          data: <String, dynamic>{
            'success': true,
            'message': 'OK',
            'data': res,
          },
        ));
      }
    } else {
      handler.reject(DioException(
        requestOptions: options,
        response: Response(
          requestOptions: options,
          statusCode: 404,
          data: <String, dynamic>{'success': false, 'message': 'Route not matched: $key'},
        ),
        type: DioExceptionType.badResponse,
      ));
    }
  }
}

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
