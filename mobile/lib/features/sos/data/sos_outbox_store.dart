import 'dart:async';

import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'sos_outbox_item.dart';

/// Persistent local store for outbox SOS items.
///
/// All public methods MUST await [ready] before touching the database.
/// [init] must be called once during app startup; its returned Future should
/// be awaited before the store is first used.
abstract class SosOutboxStore {
  Future<void> init();

  /// Completes when the store is fully initialised and ready for use.
  /// Throws [StateError] if initialisation failed.
  Future<void> get ready;

  /// Canonical upsert via [ConflictAlgorithm.replace].
  Future<void> save(SosOutboxItem item);

  Future<SosOutboxItem?> get(String clientRequestId);
  Future<List<SosOutboxItem>> getPending();
  Future<List<SosOutboxItem>> getCancelPending();
  Future<List<SosOutboxItem>> getFailed();

  /// Updates the [status] of the item identified by [clientRequestId].
  ///
  /// Optional fields:
  /// - [lastError]: error string to persist.
  /// - [retryCount]: explicit new retry count (written atomically with other
  ///   fields).  If null, the persisted value is left unchanged.
  /// - [lastAttemptAt]: explicit timestamp.  If null, the current UTC time is
  ///   used when the status requires it.
  Future<void> updateStatus(
    String clientRequestId,
    SosOutboxStatus status, {
    String? lastError,
    int? retryCount,
    DateTime? lastAttemptAt,
  });

  /// Resets a FAILED item back to PENDING with retryCount=0 and lastError=null
  /// so it receives a fresh budget of maxAttempts on the next drain cycle.
  Future<void> resetFailedToPending(String clientRequestId);

  Future<void> delete(String clientRequestId);

  /// Resets only SYNCING rows back to PENDING (e.g. on crash-recovery).
  /// Intentionally preserves CANCEL_PENDING, CANCELLATION_FAILED, FAILED.
  Future<void> resetSyncingToPending();

  Future<int> count();
}

// ---------------------------------------------------------------------------

class SqfliteSosOutboxStore implements SosOutboxStore {
  SqfliteSosOutboxStore({Database? db}) : _db = db;

  Database? _db;
  final Completer<void> _readyCompleter = Completer<void>();
  Future<void>? _initFuture;

  static const String tableName = 'sos_outbox';

  @override
  Future<void> get ready => _readyCompleter.future;

  @override
  Future<void> init() {
    _initFuture ??= _doInit();
    return _initFuture!;
  }

  Future<void> _doInit() async {
    try {
      if (_db == null) {
        final dbPath = await getDatabasesPath();
        final path = p.join(dbPath, 'geoshield_sos.db');
        _db = await openDatabase(
          path,
          version: 1,
          onCreate: (db, version) async {
            await db.execute('''
              CREATE TABLE IF NOT EXISTS $tableName (
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
        );
      }
      final db = _db;
      if (db == null) {
        throw StateError('SosOutboxStore failed to open database');
      }
      await _resetSyncing(db);
      if (!_readyCompleter.isCompleted) {
        _readyCompleter.complete();
      }
    } catch (e, st) {
      _initFuture = null; // Allow retry on subsequent init() calls
      if (!_readyCompleter.isCompleted) {
        _readyCompleter.completeError(e, st);
      }
      rethrow;
    }
  }

  Future<void> _resetSyncing(Database db) async {
    await db.update(
      tableName,
      {'status': SosOutboxStatus.pending.toDbString()},
      where: 'status = ?',
      whereArgs: [SosOutboxStatus.syncing.toDbString()],
    );
  }

  @override
  Future<void> save(SosOutboxItem item) async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    await db.insert(
      tableName,
      item.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  @override
  Future<SosOutboxItem?> get(String clientRequestId) async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    final results = await db.query(
      tableName,
      where: 'client_request_id = ?',
      whereArgs: [clientRequestId],
      limit: 1,
    );
    if (results.isEmpty) return null;
    return SosOutboxItem.fromMap(results.first);
  }

  @override
  Future<List<SosOutboxItem>> getPending() async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    final results = await db.query(
      tableName,
      where: 'status = ?',
      whereArgs: [SosOutboxStatus.pending.toDbString()],
      orderBy: 'created_at ASC',
    );
    return results.map(SosOutboxItem.fromMap).toList();
  }

  @override
  Future<List<SosOutboxItem>> getCancelPending() async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    final results = await db.query(
      tableName,
      where: 'status = ?',
      whereArgs: [SosOutboxStatus.cancelPending.toDbString()],
      orderBy: 'created_at ASC',
    );
    return results.map(SosOutboxItem.fromMap).toList();
  }

  @override
  Future<List<SosOutboxItem>> getFailed() async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    final results = await db.query(
      tableName,
      where: 'status = ?',
      whereArgs: [SosOutboxStatus.failed.toDbString()],
      orderBy: 'created_at ASC',
    );
    return results.map(SosOutboxItem.fromMap).toList();
  }

  @override
  Future<void> updateStatus(
    String clientRequestId,
    SosOutboxStatus status, {
    String? lastError,
    int? retryCount,
    DateTime? lastAttemptAt,
  }) async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    final nowIso = (lastAttemptAt ?? DateTime.now().toUtc()).toIso8601String();

    final Map<String, dynamic> values = {'status': status.toDbString()};

    // Always record the attempt time when moving to syncing, pending, or any
    // terminal/retry state so the outbox log is complete.
    if (status == SosOutboxStatus.syncing ||
        status == SosOutboxStatus.pending ||
        status == SosOutboxStatus.failed ||
        status == SosOutboxStatus.cancelPending ||
        status == SosOutboxStatus.cancellationFailed) {
      values['last_attempt_at'] = nowIso;
    }

    if (lastError != null) {
      values['last_error'] = lastError;
    }

    // Caller supplies an explicit retryCount — write it atomically.
    if (retryCount != null) {
      values['retry_count'] = retryCount;
      await db.update(
        tableName,
        values,
        where: 'client_request_id = ?',
        whereArgs: [clientRequestId],
      );
    } else if (status == SosOutboxStatus.pending && lastError != null) {
      // Auto-increment retry_count when reverting to pending with an error
      await db.rawUpdate(
        'UPDATE $tableName SET status = ?, last_attempt_at = ?, last_error = ?, retry_count = retry_count + 1 WHERE client_request_id = ?',
        [status.toDbString(), nowIso, lastError, clientRequestId],
      );
    } else {
      await db.update(
        tableName,
        values,
        where: 'client_request_id = ?',
        whereArgs: [clientRequestId],
      );
    }
  }

  @override
  Future<void> resetFailedToPending(String clientRequestId) async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    final nowIso = DateTime.now().toUtc().toIso8601String();
    await db.update(
      tableName,
      {
        'status': SosOutboxStatus.pending.toDbString(),
        'retry_count': 0,
        'last_error': null,
        'last_attempt_at': nowIso,
      },
      where: 'client_request_id = ? AND status = ?',
      whereArgs: [clientRequestId, SosOutboxStatus.failed.toDbString()],
    );
  }

  @override
  Future<void> delete(String clientRequestId) async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    await db.delete(
      tableName,
      where: 'client_request_id = ?',
      whereArgs: [clientRequestId],
    );
  }

  @override
  Future<void> resetSyncingToPending() async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    await _resetSyncing(db);
  }

  @override
  Future<int> count() async {
    await ready;
    final db = _db;
    if (db == null) throw StateError('SosOutboxStore database is not open');
    final result = await db.rawQuery('SELECT COUNT(*) as c FROM $tableName');
    return Sqflite.firstIntValue(result) ?? 0;
  }
}

// ---------------------------------------------------------------------------

class InMemorySosOutboxStore implements SosOutboxStore {
  final Map<String, SosOutboxItem> _store = {};
  final Completer<void> _readyCompleter = Completer<void>()..complete();

  @override
  Future<void> get ready => _readyCompleter.future;

  @override
  Future<void> init() async {
    await resetSyncingToPending();
  }

  @override
  Future<void> save(SosOutboxItem item) async {
    await ready;
    _store[item.clientRequestId] = item;
  }

  @override
  Future<SosOutboxItem?> get(String clientRequestId) async {
    await ready;
    return _store[clientRequestId];
  }

  @override
  Future<List<SosOutboxItem>> getPending() async {
    await ready;
    return _store.values
        .where((item) => item.status == SosOutboxStatus.pending)
        .toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
  }

  @override
  Future<List<SosOutboxItem>> getCancelPending() async {
    await ready;
    return _store.values
        .where((item) => item.status == SosOutboxStatus.cancelPending)
        .toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
  }

  @override
  Future<List<SosOutboxItem>> getFailed() async {
    await ready;
    return _store.values
        .where((item) => item.status == SosOutboxStatus.failed)
        .toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
  }

  @override
  Future<void> updateStatus(
    String clientRequestId,
    SosOutboxStatus status, {
    String? lastError,
    int? retryCount,
    DateTime? lastAttemptAt,
  }) async {
    await ready;
    final existing = _store[clientRequestId];
    if (existing == null) return;
    final now = lastAttemptAt ?? DateTime.now().toUtc();
    final effectiveRetryCount = retryCount ??
        ((status == SosOutboxStatus.pending && lastError != null)
            ? existing.retryCount + 1
            : existing.retryCount);
    _store[clientRequestId] = existing.copyWith(
      status: status,
      lastError: lastError,
      lastAttemptAt: now,
      retryCount: effectiveRetryCount,
    );
  }

  @override
  Future<void> resetFailedToPending(String clientRequestId) async {
    await ready;
    final existing = _store[clientRequestId];
    if (existing == null) return;
    if (existing.status != SosOutboxStatus.failed) return;
    _store[clientRequestId] = existing.copyWith(
      status: SosOutboxStatus.pending,
      retryCount: 0,
      lastAttemptAt: DateTime.now().toUtc(),
      // Clear lastError by replacing the whole item — copyWith can't set null.
    );
    // Clear lastError explicitly via a new construction (copyWith can't null it).
    final updated = _store[clientRequestId]!;
    _store[clientRequestId] = SosOutboxItem(
      clientRequestId: updated.clientRequestId,
      latitude: updated.latitude,
      longitude: updated.longitude,
      createdAt: updated.createdAt,
      status: updated.status,
      retryCount: updated.retryCount,
      lastAttemptAt: updated.lastAttemptAt,
      lastError: null,
    );
  }

  @override
  Future<void> delete(String clientRequestId) async {
    await ready;
    _store.remove(clientRequestId);
  }

  @override
  Future<void> resetSyncingToPending() async {
    for (final entry in _store.entries.toList()) {
      if (entry.value.status == SosOutboxStatus.syncing) {
        _store[entry.key] = entry.value.copyWith(status: SosOutboxStatus.pending);
      }
    }
  }

  @override
  Future<int> count() async {
    await ready;
    return _store.length;
  }
}
