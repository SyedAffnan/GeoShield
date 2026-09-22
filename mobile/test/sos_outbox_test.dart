import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/sos/data/sos_outbox_item.dart';
import 'package:geoshield_mobile/features/sos/data/sos_outbox_store.dart';

void main() {
  group('SosOutboxItem', () {
    test('serializes and deserializes correctly', () {
      final now = DateTime.utc(2026, 9, 21, 12, 0, 0);
      final item = SosOutboxItem(
        clientRequestId: 'test-req-123',
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: now,
        status: SosOutboxStatus.pending,
        retryCount: 2,
        lastAttemptAt: now,
        lastError: 'Connection refused',
      );

      final map = item.toMap();
      final fromMap = SosOutboxItem.fromMap(map);

      expect(fromMap.clientRequestId, 'test-req-123');
      expect(fromMap.latitude, closeTo(12.9716, 0.0001));
      expect(fromMap.longitude, closeTo(77.5946, 0.0001));
      expect(fromMap.createdAt, now);
      expect(fromMap.status, SosOutboxStatus.pending);
      expect(fromMap.retryCount, 2);
      expect(fromMap.lastAttemptAt, now);
      expect(fromMap.lastError, 'Connection refused');
    });

    test('copyWith updates fields correctly', () {
      final now = DateTime.utc(2026, 9, 21, 12, 0, 0);
      final item = SosOutboxItem(
        clientRequestId: 'test-req-1',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: now,
      );

      final updated = item.copyWith(
        status: SosOutboxStatus.syncing,
        retryCount: 1,
        lastError: 'Timeout',
      );

      expect(updated.clientRequestId, 'test-req-1');
      expect(updated.status, SosOutboxStatus.syncing);
      expect(updated.retryCount, 1);
      expect(updated.lastError, 'Timeout');
    });
  });

  group('InMemorySosOutboxStore', () {
    late InMemorySosOutboxStore store;

    setUp(() async {
      store = InMemorySosOutboxStore();
      await store.init();
    });

    test('save and get item', () async {
      final now = DateTime.now().toUtc();
      final item = SosOutboxItem(
        clientRequestId: 'id-1',
        latitude: 12.9,
        longitude: 77.5,
        createdAt: now,
      );

      await store.save(item);
      final fetched = await store.get('id-1');

      expect(fetched, isNotNull);
      expect(fetched!.clientRequestId, 'id-1');
      expect(await store.count(), 1);
    });

    test('getPending returns only pending items sorted by createdAt', () async {
      final t1 = DateTime.utc(2026, 9, 21, 10, 0, 0);
      final t2 = DateTime.utc(2026, 9, 21, 11, 0, 0);
      final t3 = DateTime.utc(2026, 9, 21, 12, 0, 0);

      await store.save(SosOutboxItem(
        clientRequestId: 'id-2',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: t2,
        status: SosOutboxStatus.pending,
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'id-1',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: t1,
        status: SosOutboxStatus.pending,
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'id-3',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: t3,
        status: SosOutboxStatus.synced,
      ));

      final pending = await store.getPending();
      expect(pending.length, 2);
      expect(pending[0].clientRequestId, 'id-1');
      expect(pending[1].clientRequestId, 'id-2');
    });

    test('updateStatus increments retry count when reverting to pending with error', () async {
      final item = SosOutboxItem(
        clientRequestId: 'id-retry',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
        retryCount: 0,
      );
      await store.save(item);

      await store.updateStatus('id-retry', SosOutboxStatus.syncing);
      var fetched = await store.get('id-retry');
      expect(fetched!.status, SosOutboxStatus.syncing);
      expect(fetched.retryCount, 0);

      await store.updateStatus('id-retry', SosOutboxStatus.pending, lastError: 'Network down');
      fetched = await store.get('id-retry');
      expect(fetched!.status, SosOutboxStatus.pending);
      expect(fetched.retryCount, 1);
      expect(fetched.lastError, 'Network down');
    });

    test('delete removes item from store', () async {
      final item = SosOutboxItem(
        clientRequestId: 'id-del',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
      );
      await store.save(item);
      expect(await store.count(), 1);

      await store.delete('id-del');
      expect(await store.count(), 0);
      expect(await store.get('id-del'), isNull);
    });

    test('resetSyncingToPending recovers stuck syncing items on crash', () async {
      await store.save(SosOutboxItem(
        clientRequestId: 'stuck-item',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.syncing,
      ));

      // Simulate crash recovery restart
      await store.resetSyncingToPending();

      final recovered = await store.get('stuck-item');
      expect(recovered!.status, SosOutboxStatus.pending);
    });

    test('resetSyncingToPending preserves cancelPending, cancellationFailed, and failed items', () async {
      await store.save(SosOutboxItem(
        clientRequestId: 'cancel-pending-1',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.cancelPending,
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'cancellation-failed-1',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.cancellationFailed,
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'failed-1',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.failed,
      ));

      await store.resetSyncingToPending();

      expect((await store.get('cancel-pending-1'))!.status, SosOutboxStatus.cancelPending);
      expect((await store.get('cancellation-failed-1'))!.status, SosOutboxStatus.cancellationFailed);
      expect((await store.get('failed-1'))!.status, SosOutboxStatus.failed);
    });

    test('getCancelPending and getFailed filter items correctly', () async {
      await store.save(SosOutboxItem(
        clientRequestId: 'cp-item',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.cancelPending,
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'failed-item',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.failed,
      ));

      final cpItems = await store.getCancelPending();
      expect(cpItems.length, 1);
      expect(cpItems.first.clientRequestId, 'cp-item');

      final failedItems = await store.getFailed();
      expect(failedItems.length, 1);
      expect(failedItems.first.clientRequestId, 'failed-item');
    });

    test('resetFailedToPending resets failed item to pending, clears error and resets retryCount', () async {
      await store.save(SosOutboxItem(
        clientRequestId: 'fail-to-reset',
        latitude: 12.0,
        longitude: 77.0,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.failed,
        retryCount: 5,
        lastError: 'Fatal network failure',
      ));

      await store.resetFailedToPending('fail-to-reset');

      final item = await store.get('fail-to-reset');
      expect(item, isNotNull);
      expect(item!.status, SosOutboxStatus.pending);
      expect(item.retryCount, 0);
      expect(item.lastError, isNull);
    });
  });
}
