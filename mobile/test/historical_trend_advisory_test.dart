import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/risk/data/historical_advisory_model.dart';
import 'package:geoshield_mobile/features/risk/presentation/historical_trend_advisory_card.dart';

void main() {
  group('HistoricalTrendAdvisoryModel Tests', () {
    test('fromJson parses available advisory with full provenance', () {
      final json = {
        'status': 'AVAILABLE',
        'advisoryType': 'HISTORICAL_ACCIDENT_TREND_ADVISORY',
        'geographicLevel': 'STATE_UT',
        'geographicUnit': 'Karnataka',
        'parentUnit': 'India',
        'targetYear': 2024,
        'predictedAccidentSeverity': 27.89,
        'severityMetricUnit': 'fatalities_per_100_accidents',
        'advisoryNotice': 'Historical road-safety model provides a 2024 temporal-holdout estimate.',
        'scopeDisclaimer': HistoricalTrendAdvisoryModel(
          status: 'AVAILABLE',
          advisoryType: '',
          geographicLevel: 'STATE_UT',
          advisoryNotice: '',
          scopeDisclaimer: 'Custom disclaimer',
        ).scopeDisclaimer,
        'provenance': {
          'experimentIdentifier': 'RF-STATE-EXP-A',
          'predictionSource': '2024_TEMPORAL_HOLDOUT',
          'advisoryReleaseVersion': '1.0.0-p0',
          'modelArtifact': null,
          'modelArtifactStatus': 'UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT',
          'datasetIdentifier': 'geosheild_ml_lagged_features.csv',
          'datasetHashSha256': 'e2eba6a1f3276d9b345827d22ac3dfd1a524107d6539eb23a96aac211aff5167',
          'artifactGeneratedAt': '2026-09-22T20:30:00Z',
          'trainingTimestamp': null,
          'trainingTimestampStatus': 'UNAVAILABLE_NOT_RECORDED_IN_ORIGINAL_EXPERIMENT',
          'trainingPeriod': '2021-2023',
          'trainingRows': 107,
          'holdoutYear': 2024,
          'evaluationRows': 35,
          'evaluationMetrics': {'r2': 0.9122, 'mae': 3.879, 'rmse': 6.1331, 'baseline_mae': 16.4128},
          'modelConfiguration': {'n_estimators': 300, 'max_depth': 5, 'random_state': 42},
          'limitations': 'Advisory only.',
        },
      };

      final model = HistoricalTrendAdvisoryModel.fromJson(json);

      expect(model.status, 'AVAILABLE');
      expect(model.isAvailable, true);
      expect(model.geographicLevel, 'STATE_UT');
      expect(model.geographicUnit, 'Karnataka');
      expect(model.targetYear, 2024);
      expect(model.predictedAccidentSeverity, 27.89);
      expect(model.severityMetricUnit, 'fatalities_per_100_accidents');
      expect(model.provenance, isNotNull);
      expect(model.provenance!.experimentIdentifier, 'RF-STATE-EXP-A');
      expect(model.provenance!.predictionSource, '2024_TEMPORAL_HOLDOUT');
      expect(model.provenance!.modelArtifactStatus, 'UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT');
      expect(model.provenance!.evaluationMetrics['r2'], 0.9122);
      expect(model.provenance!.modelConfiguration['n_estimators'], 300);
    });

    test('fromJson parses unavailable advisory cleanly', () {
      final json = {
        'status': 'UNAVAILABLE',
        'advisoryType': 'HISTORICAL_ACCIDENT_TREND_ADVISORY',
        'geographicLevel': 'STATE_UT',
        'advisoryNotice': 'Location coordinates could not be resolved.',
        'scopeDisclaimer': 'Mandatory disclaimer',
      };

      final model = HistoricalTrendAdvisoryModel.fromJson(json);

      expect(model.status, 'UNAVAILABLE');
      expect(model.isAvailable, false);
      expect(model.predictedAccidentSeverity, isNull);
      expect(model.provenance, isNull);
    });
  });

  group('HistoricalTrendAdvisoryCard Widget Tests', () {
    testWidgets('renders available advisory with mandatory disclaimer and opens provenance sheet',
        (tester) async {
      final advisory = HistoricalTrendAdvisoryModel(
        status: 'AVAILABLE',
        advisoryType: 'HISTORICAL_ACCIDENT_TREND_ADVISORY',
        geographicLevel: 'STATE_UT',
        geographicUnit: 'Karnataka',
        parentUnit: 'India',
        targetYear: 2024,
        predictedAccidentSeverity: 27.89,
        severityMetricUnit: 'fatalities_per_100_accidents',
        advisoryNotice: 'Historical road-safety model provides a 2024 temporal-holdout model estimate.',
        scopeDisclaimer:
            'This is a retrospective 2024 model-evaluation result for regional road-accident severity. '
            'It is not a current condition, forecast, tourist-safety assessment, or GeoShield Safety Score.',
        provenance: const ModelProvenanceModel(
          experimentIdentifier: 'RF-STATE-EXP-A',
          predictionSource: '2024_TEMPORAL_HOLDOUT',
          advisoryReleaseVersion: '1.0.0-p0',
          modelArtifactStatus: 'UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT',
          trainingTimestampStatus: 'UNAVAILABLE_NOT_RECORDED_IN_ORIGINAL_EXPERIMENT',
          datasetIdentifier: 'geosheild_ml_lagged_features.csv',
          datasetHashSha256: 'e2eba6a1f3276d9b345827d22ac3dfd1a524107d6539eb23a96aac211aff5167',
          trainingPeriod: '2021-2023',
          trainingRows: 107,
          holdoutYear: 2024,
          evaluationRows: 35,
          evaluationMetrics: {'r2': 0.9122, 'mae': 3.879, 'rmse': 6.1331, 'baseline_mae': 16.4128},
          modelConfiguration: {'n_estimators': 300, 'max_depth': 5, 'random_state': 42},
          limitations: 'Contextual transport awareness only.',
        ),
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: HistoricalTrendAdvisoryCard(advisory: advisory),
            ),
          ),
        ),
      );

      // Verify card titles
      expect(find.text('Historical Trend Advisory'), findsOneWidget);
      expect(
        find.text('Retrospective 2024 regional transport-accident severity indicator'),
        findsOneWidget,
      );

      // Verify regional state and metrics
      expect(find.text('STATE / UT: KARNATAKA'), findsOneWidget);
      expect(find.text('27.9'), findsOneWidget);
      expect(find.text('fatalities per 100 accidents (model-estimated)'), findsOneWidget);

      // Verify mandatory disclaimer
      expect(
        find.textContaining('This is a retrospective 2024 model-evaluation result'),
        findsOneWidget,
      );

      // Verify provenance button is present
      final provenanceButton = find.text('View Model Provenance');
      expect(provenanceButton, findsOneWidget);

      // Tap button and verify modal bottom sheet opens
      await tester.tap(provenanceButton);
      await tester.pumpAndSettle();

      expect(find.text('Model Provenance & Audit'), findsOneWidget);
      expect(find.text('RF-STATE-EXP-A', skipOffstage: false), findsOneWidget);
      expect(find.text('2024_TEMPORAL_HOLDOUT', skipOffstage: false), findsOneWidget);
      expect(find.text('Random Forest Regressor', skipOffstage: false), findsOneWidget);
      expect(find.text('UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT', skipOffstage: false), findsOneWidget);
    });

    testWidgets('renders unavailable state with message without crashing', (tester) async {
      const advisory = HistoricalTrendAdvisoryModel(
        status: 'UNAVAILABLE',
        advisoryType: 'HISTORICAL_ACCIDENT_TREND_ADVISORY',
        geographicLevel: 'STATE_UT',
        advisoryNotice: 'Current tourist GPS location is required to determine the regional advisory.',
        scopeDisclaimer: 'Mandatory disclaimer',
      );

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: HistoricalTrendAdvisoryCard(advisory: advisory),
          ),
        ),
      );

      expect(find.text('Historical Trend Advisory'), findsOneWidget);
      expect(
        find.text('Current tourist GPS location is required to determine the regional advisory.'),
        findsOneWidget,
      );
      expect(find.text('View Model Provenance'), findsNothing);
    });
  });
}
