import '../../../core/location/device_location_service.dart';
import '../../../core/network/api_client.dart';
import '../../location/data/location_repository.dart';
import 'sachet_alert_model.dart';

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

class RiskDataCompletenessModel {
  const RiskDataCompletenessModel({
    required this.availableFactorCount,
    required this.expectedLiveFactorCount,
    required this.availabilityRatio,
    required this.degraded,
    required this.missingFactors,
    required this.missingReasons,
  });
  final int availableFactorCount;
  final int expectedLiveFactorCount;
  final double availabilityRatio;
  final bool degraded;
  final List<String> missingFactors;
  final Map<String, String> missingReasons;

  factory RiskDataCompletenessModel.fromJson(Map<String, dynamic> json) =>
      RiskDataCompletenessModel(
        availableFactorCount: json['availableFactorCount'] as int? ?? 0,
        expectedLiveFactorCount: json['expectedLiveFactorCount'] as int? ?? 5,
        availabilityRatio:
            (json['availabilityRatio'] as num?)?.toDouble() ?? 0.0,
        degraded: json['degraded'] as bool? ?? false,
        missingFactors: (json['missingFactors'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        missingReasons: (json['missingReasons'] as Map<String, dynamic>?)
                ?.map((k, v) => MapEntry(k, v.toString())) ??
            const {},
      );
}

class RiskFactorDetailModel {
  const RiskFactorDetailModel({
    required this.factor,
    required this.weight,
    required this.available,
    this.rawValue,
    this.normalizedValue,
    this.weightedContribution,
    this.reason,
    this.explanation,
    this.source,
    this.sourceType,
    this.sourceIdentifier,
    this.observedAt,
    this.freshnessSeconds,
    this.geographicScope,
    this.normalizationDetails,
  });
  final String factor;
  final double weight;
  final bool available;
  final String? rawValue;
  final double? normalizedValue;
  final double? weightedContribution;
  final String? reason;
  final String? explanation;
  final String? source;
  final String? sourceType;
  final String? sourceIdentifier;
  final DateTime? observedAt;
  final int? freshnessSeconds;
  final String? geographicScope;
  final String? normalizationDetails;

  factory RiskFactorDetailModel.fromJson(Map<String, dynamic> json) =>
      RiskFactorDetailModel(
        factor: (json['factor'] ?? json['factorName'] ?? '') as String,
        weight: (json['weight'] as num).toDouble(),
        available: json['available'] as bool,
        rawValue: json['rawValue'] as String?,
        normalizedValue: (json['normalizedValue'] as num?)?.toDouble(),
        weightedContribution:
            (json['weightedContribution'] as num?)?.toDouble(),
        reason: json['reason'] as String?,
        explanation: json['explanation'] as String?,
        source: json['source'] as String?,
        sourceType: json['sourceType'] as String?,
        sourceIdentifier: json['sourceIdentifier'] as String?,
        observedAt: json['observedAt'] != null
            ? DateTime.tryParse(json['observedAt'].toString())
            : null,
        freshnessSeconds: (json['freshnessSeconds'] as num?)?.toInt(),
        geographicScope: json['geographicScope'] as String?,
        normalizationDetails: json['normalizationDetails'] as String?,
      );
}

class RiskResult {
  const RiskResult({
    required this.safetyScore,
    required this.riskLevel,
    required this.recommendation,
    required this.factors,
    this.overrideActive = false,
    this.effectiveRiskLevel,
    this.activeDisasterAlert,
    this.decisionId,
    this.dataCompleteness,
    this.factorDetails = const [],
  });
  final double safetyScore;
  final String riskLevel;
  final String recommendation;
  final List<RiskFactor> factors;
  final bool overrideActive;
  final String? effectiveRiskLevel;
  final SachetAlertModel? activeDisasterAlert;
  final String? decisionId;
  final RiskDataCompletenessModel? dataCompleteness;
  final List<RiskFactorDetailModel> factorDetails;

  factory RiskResult.fromJson(Map<String, dynamic> json) => RiskResult(
        safetyScore: (json['safetyScore'] as num).toDouble(),
        riskLevel: json['riskLevel'] as String,
        recommendation: json['recommendation'] as String,
        factors: (json['contributingFactors'] as List<dynamic>)
            .map((item) =>
                RiskFactor.fromJson(Map<String, dynamic>.from(item as Map)))
            .toList(growable: false),
        overrideActive: json['overrideActive'] as bool? ?? false,
        effectiveRiskLevel: json['effectiveRiskLevel'] as String?,
        activeDisasterAlert: json['activeDisasterAlert'] != null
            ? SachetAlertModel.fromJson(
                Map<String, dynamic>.from(json['activeDisasterAlert'] as Map))
            : null,
        decisionId: json['decisionId'] as String?,
        dataCompleteness: json['dataCompleteness'] != null
            ? RiskDataCompletenessModel.fromJson(
                Map<String, dynamic>.from(json['dataCompleteness'] as Map))
            : null,
        factorDetails: (json['factorDetails'] as List<dynamic>?)
                ?.map((item) => RiskFactorDetailModel.fromJson(
                    Map<String, dynamic>.from(item as Map)))
                .toList(growable: false) ??
            const [],
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
