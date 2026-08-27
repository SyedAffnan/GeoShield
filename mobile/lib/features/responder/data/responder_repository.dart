import '../../../core/network/api_client.dart';

class ResponderIncident {
  const ResponderIncident({
    required this.incidentId,
    required this.reporterId,
    required this.reporterUsername,
    required this.reporterFullName,
    required this.reporterPhoneNumber,
    required this.incidentType,
    required this.description,
    required this.latitude,
    required this.longitude,
    required this.status,
    this.createdAt,
  });

  factory ResponderIncident.fromJson(Map<String, dynamic> json) {
    return ResponderIncident(
      incidentId: json['incidentId'] as String? ?? '',
      reporterId: json['reporterId'] as String? ?? '',
      reporterUsername: json['reporterUsername'] as String? ?? '',
      reporterFullName: json['reporterFullName'] as String? ?? '',
      reporterPhoneNumber: json['reporterPhoneNumber'] as String? ?? '',
      incidentType: json['incidentType'] as String? ?? '',
      description: json['description'] as String? ?? '',
      latitude: (json['latitude'] as num?)?.toDouble() ?? 0.0,
      longitude: (json['longitude'] as num?)?.toDouble() ?? 0.0,
      status: json['status'] as String? ?? '',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
    );
  }

  final String incidentId;
  final String reporterId;
  final String reporterUsername;
  final String reporterFullName;
  final String reporterPhoneNumber;
  final String incidentType;
  final String description;
  final double latitude;
  final double longitude;
  final String status;
  final DateTime? createdAt;
}

class SosItem {
  const SosItem({
    required this.sosId,
    required this.userId,
    required this.username,
    required this.fullName,
    required this.phoneNumber,
    required this.latitude,
    required this.longitude,
    required this.status,
    this.triggeredAt,
  });

  factory SosItem.fromJson(Map<String, dynamic> json) {
    return SosItem(
      sosId: json['sosId'] as String? ?? '',
      userId: json['userId'] as String? ?? '',
      username: json['username'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      phoneNumber: json['phoneNumber'] as String? ?? '',
      latitude: (json['latitude'] as num?)?.toDouble() ?? 0.0,
      longitude: (json['longitude'] as num?)?.toDouble() ?? 0.0,
      status: json['status'] as String? ?? '',
      triggeredAt: json['triggeredAt'] != null
          ? DateTime.tryParse(json['triggeredAt'] as String)
          : null,
    );
  }

  final String sosId;
  final String userId;
  final String username;
  final String fullName;
  final String phoneNumber;
  final double latitude;
  final double longitude;
  final String status;
  final DateTime? triggeredAt;
}

class ResponderRepository {
  ResponderRepository(this._client);
  final GeoShieldApiClient _client;

  Future<List<ResponderIncident>> fetchIncidentQueue() async {
    final data = await _client.getData('/api/v1/responder/incidents/queue');
    final list = data as List<dynamic>? ?? <dynamic>[];
    return list
        .map((item) => ResponderIncident.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<ResponderIncident> fetchIncident(String incidentId) async {
    final data =
        await _client.getData('/api/v1/responder/incidents/$incidentId');
    return ResponderIncident.fromJson(data as Map<String, dynamic>);
  }

  Future<ResponderIncident> updateIncidentStatus(
      String incidentId, String status) async {
    final data = await _client.patchData(
      '/api/v1/responder/incidents/$incidentId/status',
      data: {'status': status},
    );
    return ResponderIncident.fromJson(data);
  }

  Future<List<SosItem>> fetchSosQueue() async {
    final data = await _client.getData('/api/v1/sos/queue');
    final list = data as List<dynamic>? ?? <dynamic>[];
    return list
        .map((item) => SosItem.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<SosItem> updateSosStatus(String sosId, String status) async {
    final data = await _client.patchData(
      '/api/v1/sos/$sosId/status',
      data: {'status': status},
    );
    return SosItem.fromJson(data);
  }
}
