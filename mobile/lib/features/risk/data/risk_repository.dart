import '../../../core/network/api_client.dart';
import '../../../core/network/auth_exception.dart';

class CurrentLocation {
  const CurrentLocation(
      {required this.latitude,
      required this.longitude,
      required this.timestamp});
  final double latitude;
  final double longitude;
  final DateTime? timestamp;

  factory CurrentLocation.fromJson(Map<String, dynamic> json) =>
      CurrentLocation(
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        timestamp: DateTime.tryParse(json['timestamp'] as String? ?? ''),
      );
}

class RiskFactor {
  const RiskFactor({
    required this.factor,
    required this.available,
    required this.normalizedRisk,
    required this.contribution,
    required this.explanation,
  });
  final String factor;
  final bool available;
  final double? normalizedRisk;
  final double contribution;
  final String explanation;

  factory RiskFactor.fromJson(Map<String, dynamic> json) => RiskFactor(
        factor: json['factor'] as String,
        available: json['available'] as bool,
        normalizedRisk: (json['normalizedRisk'] as num?)?.toDouble(),
        contribution: (json['contribution'] as num).toDouble(),
        explanation: json['explanation'] as String,
      );
}

class RiskResult {
  const RiskResult({
    required this.safetyScore,
    required this.riskLevel,
    required this.recommendation,
    required this.factors,
  });
  final double safetyScore;
  final String riskLevel;
  final String recommendation;
  final List<RiskFactor> factors;

  factory RiskResult.fromJson(Map<String, dynamic> json) => RiskResult(
        safetyScore: (json['safetyScore'] as num).toDouble(),
        riskLevel: json['riskLevel'] as String,
        recommendation: json['recommendation'] as String,
        factors: (json['contributingFactors'] as List<dynamic>)
            .map((item) =>
                RiskFactor.fromJson(Map<String, dynamic>.from(item as Map)))
            .toList(growable: false),
      );
}

class RiskDashboardData {
  const RiskDashboardData({required this.location, required this.risk});
  final CurrentLocation location;
  final RiskResult risk;
}

class LocationUnavailableException implements Exception {
  const LocationUnavailableException();
}

class RiskRepository {
  RiskRepository(this._client);
  final GeoShieldApiClient _client;

  Future<RiskDashboardData> loadDashboard() async {
    final CurrentLocation location;
    try {
      location =
          CurrentLocation.fromJson(await _client.getData('/api/v1/locations'));
    } on AuthException {
      rethrow;
    } catch (_) {
      throw const LocationUnavailableException();
    }
    final risk = RiskResult.fromJson(await _client.getData('/api/v1/risk'));
    return RiskDashboardData(location: location, risk: risk);
  }
}
