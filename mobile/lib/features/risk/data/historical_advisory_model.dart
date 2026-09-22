class ModelProvenanceModel {
  const ModelProvenanceModel({
    required this.experimentIdentifier,
    required this.predictionSource,
    required this.advisoryReleaseVersion,
    this.modelArtifact,
    required this.modelArtifactStatus,
    this.datasetIdentifier,
    this.datasetHashSha256,
    this.artifactGeneratedAt,
    this.trainingTimestamp,
    required this.trainingTimestampStatus,
    this.trainingPeriod,
    this.trainingRows,
    this.holdoutYear,
    this.evaluationRows,
    this.evaluationMetrics = const {},
    this.modelConfiguration = const {},
    this.limitations,
  });

  final String experimentIdentifier;
  final String predictionSource;
  final String advisoryReleaseVersion;
  final String? modelArtifact;
  final String modelArtifactStatus;
  final String? datasetIdentifier;
  final String? datasetHashSha256;
  final String? artifactGeneratedAt;
  final String? trainingTimestamp;
  final String trainingTimestampStatus;
  final String? trainingPeriod;
  final int? trainingRows;
  final int? holdoutYear;
  final int? evaluationRows;
  final Map<String, dynamic> evaluationMetrics;
  final Map<String, dynamic> modelConfiguration;
  final String? limitations;

  factory ModelProvenanceModel.fromJson(Map<String, dynamic> json) =>
      ModelProvenanceModel(
        experimentIdentifier: json['experimentIdentifier'] as String? ?? '',
        predictionSource: json['predictionSource'] as String? ?? '',
        advisoryReleaseVersion:
            json['advisoryReleaseVersion'] as String? ?? '',
        modelArtifact: json['modelArtifact'] as String?,
        modelArtifactStatus: json['modelArtifactStatus'] as String? ?? '',
        datasetIdentifier: json['datasetIdentifier'] as String?,
        datasetHashSha256: json['datasetHashSha256'] as String?,
        artifactGeneratedAt: json['artifactGeneratedAt'] as String?,
        trainingTimestamp: json['trainingTimestamp'] as String?,
        trainingTimestampStatus:
            json['trainingTimestampStatus'] as String? ?? '',
        trainingPeriod: json['trainingPeriod'] as String?,
        trainingRows: json['trainingRows'] as int?,
        holdoutYear: json['holdoutYear'] as int?,
        evaluationRows: json['evaluationRows'] as int?,
        evaluationMetrics:
            (json['evaluationMetrics'] as Map<String, dynamic>?) ?? const {},
        modelConfiguration:
            (json['modelConfiguration'] as Map<String, dynamic>?) ?? const {},
        limitations: json['limitations'] as String?,
      );
}

class HistoricalTrendAdvisoryModel {
  const HistoricalTrendAdvisoryModel({
    required this.status,
    required this.advisoryType,
    required this.geographicLevel,
    this.geographicUnit,
    this.parentUnit,
    this.targetYear,
    this.predictedAccidentSeverity,
    this.severityMetricUnit,
    required this.advisoryNotice,
    required this.scopeDisclaimer,
    this.provenance,
  });

  final String status;
  final String advisoryType;
  final String geographicLevel;
  final String? geographicUnit;
  final String? parentUnit;
  final int? targetYear;
  final double? predictedAccidentSeverity;
  final String? severityMetricUnit;
  final String advisoryNotice;
  final String scopeDisclaimer;
  final ModelProvenanceModel? provenance;

  bool get isAvailable =>
      status == 'AVAILABLE' && predictedAccidentSeverity != null;

  factory HistoricalTrendAdvisoryModel.fromJson(Map<String, dynamic> json) =>
      HistoricalTrendAdvisoryModel(
        status: json['status'] as String? ?? 'UNAVAILABLE',
        advisoryType: json['advisoryType'] as String? ?? '',
        geographicLevel: json['geographicLevel'] as String? ?? 'STATE_UT',
        geographicUnit: json['geographicUnit'] as String?,
        parentUnit: json['parentUnit'] as String?,
        targetYear: json['targetYear'] as int?,
        predictedAccidentSeverity:
            (json['predictedAccidentSeverity'] as num?)?.toDouble(),
        severityMetricUnit: json['severityMetricUnit'] as String?,
        advisoryNotice: json['advisoryNotice'] as String? ?? '',
        scopeDisclaimer: json['scopeDisclaimer'] as String? ?? '',
        provenance: json['provenance'] != null
            ? ModelProvenanceModel.fromJson(
                Map<String, dynamic>.from(json['provenance'] as Map))
            : null,
      );
}
