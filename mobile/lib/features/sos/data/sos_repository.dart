import 'dart:math';

import '../../../core/network/api_client.dart';

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
  });

  final String sosId;
  final double latitude;
  final double longitude;
  final SosStatusValue status;
  final DateTime triggeredAt;
  final String? clientRequestId;

  factory SosAlert.fromJson(Map<String, dynamic> json) => SosAlert(
        sosId: json['sosId'] as String,
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        status: SosStatusValue.fromString(json['status'] as String? ?? 'PENDING'),
        triggeredAt: DateTime.tryParse(json['triggeredAt'] as String? ?? '') ??
            DateTime.now().toUtc(),
        clientRequestId: json['clientRequestId'] as String?,
      );
}

/// Data-layer wrapper for Tourist SOS endpoints.
///
/// Calls reuse the same [GeoShieldApiClient] used by every other repository —
/// no hardcoded URLs, the runtime base-URL override applies automatically.
class SosRepository {
  SosRepository(this._client);
  final GeoShieldApiClient _client;

  /// Creates a new SOS alert for the authenticated tourist.
  ///
  /// [clientRequestId] is a random UUID generated on the device; the backend
  /// uses it for idempotency (duplicate POSTs with the same UUID return the
  /// same SOS rather than creating a second one).
  Future<SosAlert> createSos({
    required double latitude,
    required double longitude,
    required String clientRequestId,
  }) async {
    final data = await _client.postData(
      '/api/v1/sos',
      data: {
        'latitude': latitude,
        'longitude': longitude,
        'clientRequestId': clientRequestId,
      },
    );
    return SosAlert.fromJson(data);
  }

  /// Returns the tourist's own active SOS request (PENDING / ACKNOWLEDGED /
  /// RESPONDING), or null when no active alert exists.
  ///
  /// The backend returns HTTP 404 when there is no active SOS, which the
  /// [GeoShieldApiClient] wraps as a [ResourceNotFoundException].  We handle
  /// that case here and return null so the caller does not need to catch it.
  Future<SosAlert?> getActiveSos() async {
    try {
      final data = await _client.getData('/api/v1/sos/active');
      return SosAlert.fromJson(data as Map<String, dynamic>);
    } catch (_) {
      // 404 (no active SOS) or any network error — treat as absent.
      return null;
    }
  }

  /// Cancels the tourist's own SOS alert.
  Future<SosAlert> cancelSos(String sosId) async {
    final data = await _client.patchData(
      '/api/v1/sos/$sosId/cancel',
      data: {},
    );
    return SosAlert.fromJson(data);
  }
}

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
