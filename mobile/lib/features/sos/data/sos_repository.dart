import 'dart:math';

import '../../../core/network/api_client.dart';
import '../../../core/network/auth_exception.dart';
import '../../../core/network/conflict_exception.dart';
import '../../../core/network/network_exception.dart';
import '../../../core/network/resource_not_found_exception.dart';
import '../../../core/network/validation_exception.dart';
import 'sos_outbox_item.dart';
import 'sos_outbox_store.dart';

/// The statuses a Tourist SOS request can hold, mirroring the backend
/// [SosStatus] enum exactly. Do NOT change these values — they are serialised
/// and sent to the server as-is.
enum SosStatusValue {
  pending,
  acknowledged,
  responding,
  resolved,
  cancelled;

  static SosStatusValue fromString(String raw) => switch (raw.toUpperCase()) {
        'PENDING' => SosStatusValue.pending,
        'ACKNOWLEDGED' => SosStatusValue.acknowledged,
        'RESPONDING' => SosStatusValue.responding,
        'RESOLVED' => SosStatusValue.resolved,
        'CANCELLED' => SosStatusValue.cancelled,
        _ => SosStatusValue.pending,
      };

  bool get isActive =>
      this == SosStatusValue.pending ||
      this == SosStatusValue.acknowledged ||
      this == SosStatusValue.responding;

  bool get isTerminal =>
      this == SosStatusValue.resolved || this == SosStatusValue.cancelled;

  String get displayLabel => switch (this) {
        SosStatusValue.pending => 'Pending — Waiting for responder',
        SosStatusValue.acknowledged => 'Acknowledged — Responder notified',
        SosStatusValue.responding => 'Responding — Help is on the way',
        SosStatusValue.resolved => 'Resolved',
        SosStatusValue.cancelled => 'Cancelled',
      };
}

/// Tourist view of an SOS request, mapped from the backend [SosResponse] record.
class SosAlert {
  const SosAlert({
    required this.sosId,
    required this.latitude,
    required this.longitude,
    required this.status,
    required this.triggeredAt,
    this.clientRequestId,
    this.acknowledgedAt,
    this.respondingAt,
    this.resolvedAt,
    this.cancelledAt,
    this.isPendingDelivery = false,
  });

  final String sosId;
  final double latitude;
  final double longitude;
  final SosStatusValue status;
  final DateTime triggeredAt;
  final String? clientRequestId;
  final DateTime? acknowledgedAt;
  final DateTime? respondingAt;
  final DateTime? resolvedAt;
  final DateTime? cancelledAt;
  final bool isPendingDelivery;

  factory SosAlert.fromJson(Map<String, dynamic> json) => SosAlert(
        sosId: json['sosId'] as String,
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        status: SosStatusValue.fromString(json['status'] as String? ?? 'PENDING'),
        triggeredAt: DateTime.tryParse(json['triggeredAt'] as String? ?? '') ??
            DateTime.now().toUtc(),
        clientRequestId: json['clientRequestId'] as String?,
        acknowledgedAt: json['acknowledgedAt'] != null
            ? DateTime.tryParse(json['acknowledgedAt'] as String)
            : null,
        respondingAt: json['respondingAt'] != null
            ? DateTime.tryParse(json['respondingAt'] as String)
            : null,
        resolvedAt: json['resolvedAt'] != null
            ? DateTime.tryParse(json['resolvedAt'] as String)
            : null,
        cancelledAt: json['cancelledAt'] != null
            ? DateTime.tryParse(json['cancelledAt'] as String)
            : null,
        isPendingDelivery: false,
      );
}

/// Data-layer wrapper for Tourist SOS endpoints.
///
/// Calls reuse the same [GeoShieldApiClient] used by every other repository —
/// no hardcoded URLs, the runtime base-URL override applies automatically.
class SosRepository {
  SosRepository(this._client, {SosOutboxStore? outboxStore})
      : _outboxStore = outboxStore;

  final GeoShieldApiClient _client;
  final SosOutboxStore? _outboxStore;

  SosOutboxStore? get outboxStore => _outboxStore;

  /// Maximum total transmission attempts (including the first [dispatchOrQueueSos] call).
  static const int maxAttempts = 5;

  // ---------------------------------------------------------------------------
  // Core SOS creation
  // ---------------------------------------------------------------------------

  /// Creates a new SOS alert for the authenticated tourist directly on the backend.
  ///
  /// [clientRequestId] is passed both in the `Idempotency-Key` header and in
  /// the JSON request body for robust idempotency.
  Future<SosAlert> createSos({
    required double latitude,
    required double longitude,
    required String clientRequestId,
  }) async {
    final data = await _client.postData(
      '/api/v1/sos',
      headers: {
        'Idempotency-Key': clientRequestId,
      },
      data: {
        'latitude': latitude,
        'longitude': longitude,
        'clientRequestId': clientRequestId,
      },
    );
    return SosAlert.fromJson(data);
  }

  // ---------------------------------------------------------------------------
  // Active SOS query
  // ---------------------------------------------------------------------------

  /// Returns the tourist's own active SOS request (PENDING / ACKNOWLEDGED /
  /// RESPONDING), or [null] when no active alert exists (HTTP 404).
  ///
  /// IMPORTANT: Unlike the previous implementation, this method **propagates**
  /// [NetworkException] and any other non-404 exception. Only
  /// [ResourceNotFoundException] (HTTP 404) is silenced and converted to null,
  /// because a 404 is a definitive, non-transient server answer.
  Future<SosAlert?> getActiveSos() async {
    try {
      final data = await _client.getData('/api/v1/sos/active');
      return SosAlert.fromJson(data as Map<String, dynamic>);
    } on ResourceNotFoundException {
      // Definitively no active alert on server — not transient.
      return null;
    }
    // All other exceptions (NetworkException, AuthException, etc.) propagate!
  }

  // ---------------------------------------------------------------------------
  // Dispatch with write-ahead outbox
  // ---------------------------------------------------------------------------

  /// Dispatches an SOS alert immediately if online, or enqueues it in the local
  /// outbox if the device is offline or the network call fails.
  ///
  /// Guarantees offline resilience: never drops an alert. If delivery fails, it is
  /// preserved in the outbox with its stable [clientRequestId] and returned with
  /// [isPendingDelivery: true].
  ///
  /// Throws [StateError] only if the local SQLite write itself fails — the caller
  /// MUST display an irrecoverable "Call 112" dialog in that case.
  Future<SosAlert> dispatchOrQueueSos({
    required double latitude,
    required double longitude,
    String? clientRequestId,
  }) async {
    final store = _outboxStore;

    // Reuse a stable clientRequestId if one is already in the outbox
    // (prevents generating a fresh key on every duplicate tap).
    String effectiveId;
    SosOutboxItem? existing;
    if (clientRequestId != null) {
      effectiveId = clientRequestId;
      if (store != null) {
        existing = await store.get(effectiveId);
      }
    } else if (store != null) {
      final pending = await store.getPending();
      if (pending.isNotEmpty) {
        existing = pending.first;
        effectiveId = existing.clientRequestId;
      } else {
        effectiveId = generateClientRequestId();
      }
    } else {
      effectiveId = generateClientRequestId();
    }

    // Write-ahead: save to outbox as PENDING before any network call.
    // If reusing an existing pending item, preserve retryCount, lastAttemptAt,
    // and lastError so duplicate triggers don't reset the retry budget.
    if (store != null) {
      await store.save(SosOutboxItem(
        clientRequestId: effectiveId,
        latitude: latitude,
        longitude: longitude,
        createdAt: existing?.createdAt ?? DateTime.now().toUtc(),
        status: SosOutboxStatus.pending,
        retryCount: existing?.retryCount ?? 0,
        lastAttemptAt: existing?.lastAttemptAt,
        lastError: existing?.lastError,
      ));
    }

    try {
      if (store != null) {
        await store.updateStatus(effectiveId, SosOutboxStatus.syncing);
      }
      final serverAlert = await createSos(
        latitude: latitude,
        longitude: longitude,
        clientRequestId: effectiveId,
      );
      // Confirmed server acceptance: remove from outbox.
      if (store != null) {
        await store.delete(effectiveId);
      }
      return serverAlert;
    } on NetworkException catch (e) {
      // Transient network error on attempt: preserve in outbox as pending
      // incrementing retry count from existing if present.
      final newRetryCount = (existing?.retryCount ?? 0) + 1;
      if (store != null) {
        await store.updateStatus(
          effectiveId,
          newRetryCount >= maxAttempts
              ? SosOutboxStatus.failed
              : SosOutboxStatus.pending,
          lastError: e.message,
          retryCount: newRetryCount,
        );
      }
      return SosAlert(
        sosId: 'local-$effectiveId',
        latitude: latitude,
        longitude: longitude,
        status: SosStatusValue.pending,
        triggeredAt: existing?.createdAt ?? DateTime.now().toUtc(),
        clientRequestId: effectiveId,
        isPendingDelivery: true,
      );
    } on AuthException catch (e) {
      final newRetryCount = (existing?.retryCount ?? 0) + 1;
      if (store != null) {
        await store.updateStatus(
          effectiveId,
          SosOutboxStatus.failed,
          lastError: e.message,
          retryCount: newRetryCount,
        );
      }
      rethrow;
    } on ValidationException catch (e) {
      final newRetryCount = (existing?.retryCount ?? 0) + 1;
      if (store != null) {
        await store.updateStatus(
          effectiveId,
          SosOutboxStatus.failed,
          lastError: e.message,
          retryCount: newRetryCount,
        );
      }
      rethrow;
    } on ConflictException catch (e) {
      final newRetryCount = (existing?.retryCount ?? 0) + 1;
      if (store != null) {
        await store.updateStatus(
          effectiveId,
          SosOutboxStatus.failed,
          lastError: e.message,
          retryCount: newRetryCount,
        );
      }
      rethrow;
    } on ResourceNotFoundException catch (e) {
      final newRetryCount = (existing?.retryCount ?? 0) + 1;
      if (store != null) {
        await store.updateStatus(
          effectiveId,
          SosOutboxStatus.failed,
          lastError: e.message,
          retryCount: newRetryCount,
        );
      }
      rethrow;
    } catch (e) {
      final newRetryCount = (existing?.retryCount ?? 0) + 1;
      if (store != null) {
        await store.updateStatus(
          effectiveId,
          SosOutboxStatus.failed,
          lastError: e.toString(),
          retryCount: newRetryCount,
        );
      }
      rethrow;
    }
  }

  // ---------------------------------------------------------------------------
  // Drain outbox
  // ---------------------------------------------------------------------------

  /// Calculates exponential backoff duration based on retryCount.
  static Duration getBackoffDuration(int retryCount) {
    switch (retryCount) {
      case 0:
        return Duration.zero;
      case 1:
        return const Duration(seconds: 2);
      case 2:
        return const Duration(seconds: 5);
      case 3:
        return const Duration(seconds: 10);
      default:
        return const Duration(seconds: 15);
    }
  }

  /// Drains the outbox: reconciles CANCEL_PENDING items first, then retries
  /// PENDING delivery items up to [maxAttempts] total attempts.
  /// If [ignoreBackoff] is false, items within their exponential backoff window
  /// are skipped until the backoff duration has elapsed.
  Future<int> drainOutbox({bool ignoreBackoff = false}) async {
    final store = _outboxStore;
    if (store == null) return 0;

    await store.ready;

    // 0. Recover any crashed/orphaned syncing items back to pending.
    await store.resetSyncingToPending();

    // 1. Drain CANCEL_PENDING items first with strict target verification.
    await drainCancelPending();

    // 2. Process PENDING delivery items.
    final pendingItems = await store.getPending();
    int synced = 0;
    for (final item in pendingItems) {
      if (item.retryCount >= maxAttempts) {
        // Already exhausted all attempts — mark permanently failed.
        await store.updateStatus(
          item.clientRequestId,
          SosOutboxStatus.failed,
          lastError: 'Maximum retry attempts ($maxAttempts) exhausted.',
        );
        continue;
      }

      // Check exponential backoff if not ignored
      if (!ignoreBackoff && item.retryCount > 0 && item.lastAttemptAt != null) {
        final elapsed = DateTime.now().toUtc().difference(item.lastAttemptAt!);
        if (elapsed < getBackoffDuration(item.retryCount)) {
          continue; // In backoff window; skip for this drain cycle
        }
      }

      await store.updateStatus(item.clientRequestId, SosOutboxStatus.syncing);
      try {
        await createSos(
          latitude: item.latitude,
          longitude: item.longitude,
          clientRequestId: item.clientRequestId,
        );
        await store.delete(item.clientRequestId);
        synced++;
      } on AuthException catch (e) {
        // Permanent — user is logged out.
        await store.updateStatus(
          item.clientRequestId,
          SosOutboxStatus.failed,
          lastError: e.message,
        );
      } on ValidationException catch (e) {
        // Permanent — payload will never be valid.
        await store.updateStatus(
          item.clientRequestId,
          SosOutboxStatus.failed,
          lastError: e.message,
        );
      } on ConflictException catch (e) {
        // Permanent — server rejected as duplicate or existing active SOS.
        await store.updateStatus(
          item.clientRequestId,
          SosOutboxStatus.failed,
          lastError: e.message,
        );
      } on ResourceNotFoundException catch (e) {
        // Permanent — endpoint or tourist not found.
        await store.updateStatus(
          item.clientRequestId,
          SosOutboxStatus.failed,
          lastError: e.message,
        );
      } on NetworkException catch (e) {
        // Transient — increment retryCount atomically.
        final newCount = item.retryCount + 1;
        if (newCount >= maxAttempts) {
          await store.updateStatus(
            item.clientRequestId,
            SosOutboxStatus.failed,
            lastError: e.message,
            retryCount: newCount,
          );
        } else {
          await store.updateStatus(
            item.clientRequestId,
            SosOutboxStatus.pending,
            lastError: e.message,
            retryCount: newCount,
          );
        }
      } catch (e) {
        // Unknown / unexpected error — treat as permanent.
        await store.updateStatus(
          item.clientRequestId,
          SosOutboxStatus.failed,
          lastError: e.toString(),
        );
      }
    }
    return synced;
  }

  // ---------------------------------------------------------------------------
  // CANCEL_PENDING drain
  // ---------------------------------------------------------------------------

  /// Drains all persisted CANCEL_PENDING records using strict target verification.
  Future<void> drainCancelPending() async {
    final store = _outboxStore;
    if (store == null) return;
    final cancelItems = await store.getCancelPending();
    for (final item in cancelItems) {
      try {
        await reconcileCancelPendingItem(item);
      } catch (_) {
        // Individual item isolation: one failure must not abort remaining queue.
      }
    }
  }

  /// Reconciles a single CANCEL_PENDING outbox item against the server's active SOS.
  ///
  /// Safety contract:
  /// 1. Query the server's active SOS via [getActiveSos].
  ///    - 404 → [null]: server has no active SOS → delete the local record.
  ///    - [NetworkException]: transient → leave CANCEL_PENDING unchanged.
  /// 2. If an active server SOS is returned, compare [clientRequestId].
  ///    - Match → issue PATCH cancel. On 200 delete local record; on 409 → cancellationFailed.
  ///    - Mismatch → SAFETY GUARD: NEVER cancel the active SOS. Transition to
  ///      cancellationFailed with explicit mismatch error.
  Future<void> reconcileCancelPendingItem(SosOutboxItem item) async {
    final store = _outboxStore;
    if (store == null) return;
    try {
      // Step 2: Query the server's active SOS (404 → null, NetworkException → throws).
      final serverAlert = await getActiveSos();

      if (serverAlert == null) {
        // Step 3: HTTP 404 — server confirms no active SOS → delete local record.
        await store.delete(item.clientRequestId);
        return;
      }

      // Step 4 & 5: Active server SOS returned — compare clientRequestId.
      if (serverAlert.clientRequestId == item.clientRequestId) {
        try {
          await _client.patchData('/api/v1/sos/${serverAlert.sosId}/cancel', data: {});
          // Step 8: Confirmed cancellation — delete local record.
          await store.delete(item.clientRequestId);
        } on ConflictException catch (e) {
          // Case D: Server refuses cancellation (e.g., already RESPONDING).
          await store.updateStatus(
            item.clientRequestId,
            SosOutboxStatus.cancellationFailed,
            lastError: e.message,
          );
        } on ResourceNotFoundException {
          // HTTP 404 on cancel patch: server no longer has this SOS -> delete outbox item.
          await store.delete(item.clientRequestId);
        } on NetworkException {
          // Step 7: Network error during PATCH — leave CANCEL_PENDING for next drain.
        } catch (e) {
          // Permanent failure during PATCH cancel.
          await store.updateStatus(
            item.clientRequestId,
            SosOutboxStatus.cancellationFailed,
            lastError: e.toString(),
          );
        }
      } else {
        // Step 6: SAFETY GUARD — active SOS has a DIFFERENT clientRequestId!
        // NEVER cancel it. Protects against terminating a newer active emergency.
        await store.updateStatus(
          item.clientRequestId,
          SosOutboxStatus.cancellationFailed,
          lastError:
              'Active server SOS (${serverAlert.sosId}) belongs to a different '
              'clientRequestId (${serverAlert.clientRequestId}). '
              'Cancellation aborted to protect active alert.',
        );
      }
    } on NetworkException {
      // Step 7: Network error during getActiveSos() — preserve CANCEL_PENDING unchanged.
    } on ResourceNotFoundException {
      // HTTP 404 during getActiveSos() — server confirms no active SOS -> delete local record.
      await store.delete(item.clientRequestId);
    } catch (e) {
      // Permanent error during getActiveSos() (e.g. AuthException).
      await store.updateStatus(
        item.clientRequestId,
        SosOutboxStatus.cancellationFailed,
        lastError: e.toString(),
      );
    }
  }

  // ---------------------------------------------------------------------------
  // Cancellation reconciliation (immediate user action)
  // ---------------------------------------------------------------------------

  /// Cancels an SOS alert: either directly (if already on server) or through
  /// the 4-case outbox reconciliation protocol (if still locally queued).
  ///
  /// Case A (online active alert): PATCH cancel → delete outbox record.
  /// Case B (404 / not on server): delete local record.
  /// Case C (network error): persist complete CANCEL_PENDING record for drain.
  /// Case D (409 from server): mark cancellationFailed with server reason.
  Future<void> reconcileAndCancelSos(SosAlert alert) async {
    final store = _outboxStore;
    final targetKey = alert.clientRequestId ?? alert.sosId;
    try {
      if (!alert.isPendingDelivery) {
        // Case A: Online active alert — cancel directly via server.
        await _client.patchData('/api/v1/sos/${alert.sosId}/cancel', data: {});
        // Confirmed cancellation: delete any outbox record.
        await store?.delete(targetKey);
      } else {
        // Queued locally — discover server state first.
        final serverAlert = await getActiveSos();
        if (serverAlert == null) {
          // Case B (404 — server confirms no record exists).
          await store?.delete(targetKey);
        } else if (serverAlert.clientRequestId == targetKey) {
          // Matching active SOS — safe to cancel.
          await _client.patchData('/api/v1/sos/${serverAlert.sosId}/cancel', data: {});
          await store?.delete(targetKey);
        } else {
          // Active SOS has DIFFERENT clientRequestId — SAFETY GUARD.
          await store?.updateStatus(
            targetKey,
            SosOutboxStatus.cancellationFailed,
            lastError:
                'Active server SOS belongs to a different clientRequestId '
                '(${serverAlert.clientRequestId}). Cancellation aborted.',
          );
        }
      }
    } on ResourceNotFoundException {
      // Case B (404) — server confirms SOS does not exist.
      await store?.delete(targetKey);
    } on ConflictException catch (e) {
      // Case D (409) — server refuses cancellation (e.g., already RESPONDING).
      await store?.updateStatus(
        targetKey,
        SosOutboxStatus.cancellationFailed,
        lastError: e.message,
      );
    } on NetworkException catch (e) {
      // Case C — network state unknown. Persist a COMPLETE CANCEL_PENDING record.
      final cancelRecord = SosOutboxItem(
        clientRequestId: targetKey,
        latitude: alert.latitude,
        longitude: alert.longitude,
        createdAt: alert.triggeredAt,
        status: SosOutboxStatus.cancelPending,
        retryCount: 0,
        lastAttemptAt: DateTime.now().toUtc(),
        lastError: 'Cancellation pending network: ${e.message}',
      );
      await store?.save(cancelRecord); // Upsert via ConflictAlgorithm.replace
    }
  }

  // ---------------------------------------------------------------------------
  // Manual retry
  // ---------------------------------------------------------------------------

  /// Resets a FAILED alert back to PENDING with a fresh retry budget and
  /// immediately attempts to drain the outbox.
  ///
  /// Per contract: sets retryCount=0 and lastError=null so the item is not
  /// immediately failed again on the next drain cycle.
  Future<void> retryFailedAlert(String clientRequestId) async {
    final store = _outboxStore;
    if (store == null) return;
    await store.ready;
    final item = await store.get(clientRequestId);
    if (item == null || item.status != SosOutboxStatus.failed) {
      return; // Only retry genuine FAILED items
    }
    await store.resetFailedToPending(clientRequestId);
    await drainOutbox(ignoreBackoff: true);
  }
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

/// Generates a random UUID v4 string suitable as a clientRequestId.
String generateClientRequestId() {
  final rng = Random.secure();
  final bytes = List<int>.generate(16, (_) => rng.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  String hex(int n) => n.toRadixString(16).padLeft(2, '0');
  return [
    bytes.sublist(0, 4).map(hex).join(),
    bytes.sublist(4, 6).map(hex).join(),
    bytes.sublist(6, 8).map(hex).join(),
    bytes.sublist(8, 10).map(hex).join(),
    bytes.sublist(10, 16).map(hex).join(),
  ].join('-');
}
