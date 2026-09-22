import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/sos/data/sos_outbox_item.dart';
import 'package:geoshield_mobile/features/sos/data/sos_outbox_store.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  group('SqfliteSosOutboxStore Real SQLite Tests', () {
    late Database db;
    late SqfliteSosOutboxStore store;

    setUp(() async {
      // Open fresh in-memory database with the real production schema
      db = await databaseFactoryFfi.openDatabase(
        inMemoryDatabasePath,
        options: OpenDatabaseOptions(
          version: 1,
          onCreate: (db, version) async {
            await db.execute('''
              CREATE TABLE IF NOT EXISTS ${SqfliteSosOutboxStore.tableName} (
                client_request_id TEXT PRIMARY KEY,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                created_at TEXT NOT NULL,
                status TEXT NOT NULL,
                retry_count INTEGER NOT NULL DEFAULT 0,
                last_attempt_at TEXT,
                last_error TEXT
              )
            ''');
          },
        ),
      );
      store = SqfliteSosOutboxStore(db: db);
      await store.init();
    });

    tearDown(() async {
      await db.close();
    });

    test('ready completes after init', () async {
      expect(store.ready, completes);
      expect(await store.count(), 0);
    });

    test('concurrent init() calls return same Future safely', () async {
      final freshStore = SqfliteSosOutboxStore(db: db);
      final f1 = freshStore.init();
      final f2 = freshStore.init();
      expect(identical(f1, f2), isTrue);
      await Future.wait<void>([f1, f2]);
      expect(freshStore.ready, completes);
    });

    test('save and get round-trips an outbox item accurately', () async {
      final now = DateTime.now().toUtc();
      final item = SosOutboxItem(
        clientRequestId: 'req-1',
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: now,
        status: SosOutboxStatus.pending,
        retryCount: 0,
      );

      await store.save(item);
      final retrieved = await store.get('req-1');

      expect(retrieved, isNotNull);
      expect(retrieved!.clientRequestId, 'req-1');
      expect(retrieved.latitude, closeTo(12.9716, 0.0001));
      expect(retrieved.longitude, closeTo(77.5946, 0.0001));
      expect(retrieved.status, SosOutboxStatus.pending);
      expect(retrieved.retryCount, 0);
      expect(retrieved.lastError, isNull);
    });

    test('save performs canonical upsert on conflict replace', () async {
      final now = DateTime.now().toUtc();
      final item1 = SosOutboxItem(
        clientRequestId: 'req-1',
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: now,
        status: SosOutboxStatus.pending,
      );
      await store.save(item1);

      final item2 = SosOutboxItem(
        clientRequestId: 'req-1',
        latitude: 12.9716,
        longitude: 77.5946,
        createdAt: now,
        status: SosOutboxStatus.cancelPending,
        lastError: 'Network failure during cancel',
      );
      await store.save(item2);

      final retrieved = await store.get('req-1');
      expect(retrieved!.status, SosOutboxStatus.cancelPending);
      expect(retrieved.lastError, 'Network failure during cancel');
      expect(await store.count(), 1);
    });

    test('getPending, getCancelPending, and getFailed return correct partitions', () async {
      final now = DateTime.now().toUtc();
      await store.save(SosOutboxItem(
        clientRequestId: 'pending-1',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: now,
        status: SosOutboxStatus.pending,
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'cancel-1',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: now.add(const Duration(seconds: 1)),
        status: SosOutboxStatus.cancelPending,
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'failed-1',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: now.add(const Duration(seconds: 2)),
        status: SosOutboxStatus.failed,
      ));

      final pending = await store.getPending();
      expect(pending.map((e) => e.clientRequestId), ['pending-1']);

      final cancelPending = await store.getCancelPending();
      expect(cancelPending.map((e) => e.clientRequestId), ['cancel-1']);

      final failed = await store.getFailed();
      expect(failed.map((e) => e.clientRequestId), ['failed-1']);
    });

    test('updateStatus updates fields and explicit retryCount', () async {
      await store.save(SosOutboxItem(
        clientRequestId: 'req-1',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.syncing,
      ));

      await store.updateStatus(
        'req-1',
        SosOutboxStatus.failed,
        lastError: 'HTTP 401 Unauthorized',
        retryCount: 3,
      );

      final updated = await store.get('req-1');
      expect(updated!.status, SosOutboxStatus.failed);
      expect(updated.lastError, 'HTTP 401 Unauthorized');
      expect(updated.retryCount, 3);
      expect(updated.lastAttemptAt, isNotNull);
    });

    test('resetFailedToPending resets ONLY items in FAILED status', () async {
      final now = DateTime.now().toUtc();
      await store.save(SosOutboxItem(
        clientRequestId: 'failed-item',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: now,
        status: SosOutboxStatus.failed,
        retryCount: 5,
        lastError: 'Max attempts reached',
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'cancel-pending-item',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: now,
        status: SosOutboxStatus.cancelPending,
        retryCount: 1,
        lastError: 'Network down',
      ));

      // Reset the failed item
      await store.resetFailedToPending('failed-item');
      final resetItem = await store.get('failed-item');
      expect(resetItem!.status, SosOutboxStatus.pending);
      expect(resetItem.retryCount, 0);
      expect(resetItem.lastError, isNull);

      // Attempting to reset a non-failed item must NOT change its status
      await store.resetFailedToPending('cancel-pending-item');
      final cancelItem = await store.get('cancel-pending-item');
      expect(cancelItem!.status, SosOutboxStatus.cancelPending);
      expect(cancelItem.retryCount, 1);
      expect(cancelItem.lastError, 'Network down');
    });

    test('resetSyncingToPending recovers orphaned syncing items', () async {
      final now = DateTime.now().toUtc();
      await store.save(SosOutboxItem(
        clientRequestId: 'syncing-item',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: now,
        status: SosOutboxStatus.syncing,
      ));
      await store.save(SosOutboxItem(
        clientRequestId: 'cancel-pending-item',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: now,
        status: SosOutboxStatus.cancelPending,
      ));

      await store.resetSyncingToPending();

      final synced = await store.get('syncing-item');
      expect(synced!.status, SosOutboxStatus.pending);

      final cancelPending = await store.get('cancel-pending-item');
      expect(cancelPending!.status, SosOutboxStatus.cancelPending);
    });

    test('delete removes item from SQLite store', () async {
      await store.save(SosOutboxItem(
        clientRequestId: 'to-delete',
        latitude: 12.97,
        longitude: 77.59,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
      ));
      expect(await store.count(), 1);

      await store.delete('to-delete');
      expect(await store.count(), 0);
      expect(await store.get('to-delete'), isNull);
    });

    test('fresh database path executes openDatabase and onCreate to create schema from scratch', () async {
      final dbPath = await getDatabasesPath();
      final freshDbFile = p.join(dbPath, 'geoshield_sos.db');

      // Ensure the database file does not exist before test
      await databaseFactoryFfi.deleteDatabase(freshDbFile);

      // Create store without injecting pre-opened db (exercises default constructor and _doInit openDatabase)
      final freshStore = SqfliteSosOutboxStore();
      await freshStore.init();
      expect(freshStore.ready, completes);

      // Verify store can save, retrieve, and delete on this fresh database
      final testItem = SosOutboxItem(
        clientRequestId: 'fresh-db-test-key',
        latitude: 19.0760,
        longitude: 72.8777,
        createdAt: DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
      );
      await freshStore.save(testItem);
      expect(await freshStore.count(), 1);

      final loaded = await freshStore.get('fresh-db-test-key');
      expect(loaded, isNotNull);
      expect(loaded!.clientRequestId, 'fresh-db-test-key');
      expect(loaded.latitude, closeTo(19.0760, 0.0001));
      expect(loaded.longitude, closeTo(72.8777, 0.0001));

      // Inspect SQLite schema directly
      final directDb = await databaseFactoryFfi.openDatabase(freshDbFile);
      try {
        final tables = await directDb.rawQuery(
          "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
          [SqfliteSosOutboxStore.tableName],
        );
        expect(tables, isNotEmpty);
        expect(tables.first['name'], SqfliteSosOutboxStore.tableName);

        // Verify all required columns exist in the fresh schema
        final columns = await directDb.rawQuery(
          'PRAGMA table_info(${SqfliteSosOutboxStore.tableName})',
        );
        final colNames = columns.map((c) => c['name'] as String).toList();
        expect(colNames, containsAll([
          'client_request_id',
          'latitude',
          'longitude',
          'created_at',
          'status',
          'retry_count',
          'last_attempt_at',
          'last_error',
        ]));
      } finally {
        await freshStore.delete('fresh-db-test-key');
        expect(await freshStore.count(), 0);
        await directDb.close();
        await databaseFactoryFfi.deleteDatabase(freshDbFile);
      }
    });
  });
}
