/// Status values for an [SosOutboxItem] stored in the local SQLite outbox.
///
/// The lifecycle is:
///   PENDING → SYNCING → (deleted on 201/200)
///                     → PENDING (transient error, retryCount < maxAttempts)
///                     → FAILED (permanent 4xx OR retryCount >= maxAttempts)
///   PENDING → CANCEL_PENDING (user cancels while offline, full record saved)
///   CANCEL_PENDING → (deleted on successful cancel)
///                  → CANCELLATION_FAILED (server 409 or clientRequestId mismatch)
///   FAILED → PENDING (manual retry: retryCount=0, lastError=null)
enum SosOutboxStatus {
  pending,
  syncing,
  synced,
  failed,
  cancelPending,
  cancellationFailed;

  static SosOutboxStatus fromString(String raw) => switch (raw.toUpperCase()) {
        'PENDING' => SosOutboxStatus.pending,
        'SYNCING' => SosOutboxStatus.syncing,
        'SYNCED' => SosOutboxStatus.synced,
        'FAILED' => SosOutboxStatus.failed,
        'CANCEL_PENDING' => SosOutboxStatus.cancelPending,
        'CANCELLATION_FAILED' => SosOutboxStatus.cancellationFailed,
        _ => SosOutboxStatus.pending,
      };

  String toDbString() => switch (this) {
        SosOutboxStatus.pending => 'PENDING',
        SosOutboxStatus.syncing => 'SYNCING',
        SosOutboxStatus.synced => 'SYNCED',
        SosOutboxStatus.failed => 'FAILED',
        SosOutboxStatus.cancelPending => 'CANCEL_PENDING',
        SosOutboxStatus.cancellationFailed => 'CANCELLATION_FAILED',
      };
}

/// Represents an offline SOS request queued in the local device outbox.
class SosOutboxItem {
  const SosOutboxItem({
    required this.clientRequestId,
    required this.latitude,
    required this.longitude,
    required this.createdAt,
    this.status = SosOutboxStatus.pending,
    this.retryCount = 0,
    this.lastAttemptAt,
    this.lastError,
  });

  final String clientRequestId;
  final double latitude;
  final double longitude;
  final DateTime createdAt;
  final SosOutboxStatus status;
  final int retryCount;
  final DateTime? lastAttemptAt;
  final String? lastError;

  SosOutboxItem copyWith({
    String? clientRequestId,
    double? latitude,
    double? longitude,
    DateTime? createdAt,
    SosOutboxStatus? status,
    int? retryCount,
    DateTime? lastAttemptAt,
    String? lastError,
  }) {
    return SosOutboxItem(
      clientRequestId: clientRequestId ?? this.clientRequestId,
      latitude: latitude ?? this.latitude,
      longitude: longitude ?? this.longitude,
      createdAt: createdAt ?? this.createdAt,
      status: status ?? this.status,
      retryCount: retryCount ?? this.retryCount,
      lastAttemptAt: lastAttemptAt ?? this.lastAttemptAt,
      lastError: lastError ?? this.lastError,
    );
  }

  Map<String, dynamic> toMap() => {
        'client_request_id': clientRequestId,
        'latitude': latitude,
        'longitude': longitude,
        'created_at': createdAt.toIso8601String(),
        'status': status.toDbString(),
        'retry_count': retryCount,
        'last_attempt_at': lastAttemptAt?.toIso8601String(),
        'last_error': lastError,
      };

  factory SosOutboxItem.fromMap(Map<String, dynamic> map) {
    return SosOutboxItem(
      clientRequestId: map['client_request_id'] as String,
      latitude: (map['latitude'] as num).toDouble(),
      longitude: (map['longitude'] as num).toDouble(),
      createdAt: DateTime.tryParse(map['created_at'] as String? ?? '') ??
          DateTime.now().toUtc(),
      status: SosOutboxStatus.fromString(map['status'] as String? ?? 'PENDING'),
      retryCount: (map['retry_count'] as num?)?.toInt() ?? 0,
      lastAttemptAt: map['last_attempt_at'] != null
          ? DateTime.tryParse(map['last_attempt_at'] as String)
          : null,
      lastError: map['last_error'] as String?,
    );
  }
}
