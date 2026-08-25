import 'package:uuid/uuid.dart';

import '../../../core/network/api_client.dart';
import '../../risk/data/risk_repository.dart';

class Incident {
  const Incident(
      {required this.incidentType,
      required this.description,
      required this.status,
      required this.reportedAt});
  final String incidentType;
  final String description;
  final String status;
  final DateTime? reportedAt;

  factory Incident.fromJson(Map<String, dynamic> json) => Incident(
        incidentType: json['incidentType'] as String,
        description: json['description'] as String,
        status: json['status'] as String,
        reportedAt: DateTime.tryParse(json['reportedAt'] as String? ?? ''),
      );
}

class IncidentRepository {
  IncidentRepository(this._client);
  final GeoShieldApiClient _client;

  Future<List<Incident>> getIncidents() async => (await _client
          .getListData('/api/v1/incidents'))
      .map((item) => Incident.fromJson(Map<String, dynamic>.from(item as Map)))
      .toList(growable: false);

  Future<void> report(
      {required String type,
      required String description,
      required CurrentLocation location}) async {
    await _client.postData('/api/v1/incidents', data: {
      'incidentType': type,
      'description': description,
      'latitude': location.latitude,
      'longitude': location.longitude,
      'clientRequestId': const Uuid().v4(),
    });
  }
}
