import '../../../core/location/device_location_service.dart';
import '../../../core/network/api_client.dart';
import '../../location/data/location_repository.dart';

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

/// The device fix that was submitted, the location the backend stored for it, and
/// the backend's authoritative risk response.
class RiskDashboardData {
  const RiskDashboardData(
      {required this.fix, required this.storedLocation, required this.risk});
  final DeviceLocationFix fix;
  final CurrentLocation storedLocation;
  final RiskResult risk;
}

/// Which step of the GPS -> backend -> risk flow is currently running.
enum RiskDashboardStep { obtainingLocation, sendingLocation, loadingRisk }

enum RiskDashboardFailureKind { session, network, serverRejected }

sealed class RiskDashboardState {
  const RiskDashboardState();
}

final class RiskDashboardBusy extends RiskDashboardState {
  const RiskDashboardBusy(this.step);
  final RiskDashboardStep step;
}

/// No fix could be produced, so no risk request is made at all.
final class RiskDashboardLocationBlocked extends RiskDashboardState {
  const RiskDashboardLocationBlocked(this.reason);
  final DeviceLocationFailureReason reason;
}

final class RiskDashboardFailed extends RiskDashboardState {
  const RiskDashboardFailed(this.kind, {this.message});
  final RiskDashboardFailureKind kind;

  /// Server-authored message, when the backend rejected the request.
  final String? message;
}

final class RiskDashboardReady extends RiskDashboardState {
  const RiskDashboardReady(this.data);
  final RiskDashboardData data;
}

class RiskRepository {
  RiskRepository(this._client);
  final GeoShieldApiClient _client;

  /// Reads the backend's baseline risk for the authenticated tourist. The score,
  /// risk level, recommendation, and factor explanations are entirely server-computed.
  Future<RiskResult> getCurrentRisk() async =>
      RiskResult.fromJson(await _client.getData('/api/v1/risk') as Map<String, dynamic>);
}
