import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/risk/data/risk_repository.dart';

void main() {
  group('RiskResult Override Tests', () {
    test('fromJson parses standard baseline risk response without override', () {
      final json = {
        'safetyScore': 85.5,
        'riskLevel': 'LOW',
        'recommendation': 'Normal caution advised in this area.',
        'contributingFactors': [
          {
            'factor': 'Historical Crime',
            'available': true,
            'normalizedRisk': 0.12,
            'contribution': 3.6,
            'explanation': 'Low historical incident rate',
          },
          {
            'factor': 'Severe Weather',
            'available': true,
            'normalizedRisk': 0.05,
            'contribution': 1.0,
            'explanation': 'Clear weather conditions',
          }
        ],
        'overrideActive': false,
        'effectiveRiskLevel': null,
        'activeDisasterAlert': null,
      };

      final result = RiskResult.fromJson(json);

      expect(result.safetyScore, 85.5);
      expect(result.riskLevel, 'LOW');
      expect(result.recommendation, 'Normal caution advised in this area.');
      expect(result.factors.length, 2);
      expect(result.factors[0].factor, 'Historical Crime');
      expect(result.factors[0].contribution, 3.6);
      expect(result.overrideActive, false);
      expect(result.effectiveRiskLevel, isNull);
      expect(result.activeDisasterAlert, isNull);
    });

    test('fromJson parses moderate advisory alert with overrideActive false', () {
      final json = {
        'safetyScore': 78.0,
        'riskLevel': 'LOW',
        'recommendation': 'Advisory active in area.',
        'contributingFactors': [
          {
            'factor': 'Historical Crime',
            'available': true,
            'normalizedRisk': 0.20,
            'contribution': 6.0,
            'explanation': 'Moderate historical rate',
          }
        ],
        'overrideActive': false,
        'effectiveRiskLevel': null,
        'activeDisasterAlert': {
          'id': 'ALERT-MOD-1',
          'identifier': 'SACHET-MOD-001',
          'sender': 'imd.gov.in',
          'sentAt': '2026-09-20T12:00:00Z',
          'category': 'Met',
          'event': 'Heavy Rain Advisory',
          'urgency': 'Expected',
          'severity': 'Moderate',
          'certainty': 'Likely',
          'effectiveAt': '2026-09-20T12:00:00Z',
          'expiresAt': '2026-09-20T18:00:00Z',
          'headline': 'Expect heavy rainfall in afternoon',
          'instruction': 'Carry umbrellas and monitor local updates.',
          'isSynthetic': false,
        },
      };

      final result = RiskResult.fromJson(json);

      expect(result.safetyScore, 78.0);
      expect(result.riskLevel, 'LOW');
      expect(result.overrideActive, false);
      expect(result.effectiveRiskLevel, isNull);
      expect(result.activeDisasterAlert, isNotNull);
      expect(result.activeDisasterAlert!.event, 'Heavy Rain Advisory');
      expect(result.activeDisasterAlert!.severity, 'Moderate');
    });

    test('fromJson parses severe disaster alert with CRITICAL override active', () {
      final json = {
        'safetyScore': 90.0,
        'riskLevel': 'LOW',
        'recommendation': 'Normal baseline conditions.',
        'contributingFactors': [
          {
            'factor': 'Historical Crime',
            'available': true,
            'normalizedRisk': 0.10,
            'contribution': 3.0,
            'explanation': 'Safe tourist area',
          }
        ],
        'overrideActive': true,
        'effectiveRiskLevel': 'CRITICAL',
        'activeDisasterAlert': {
          'id': 'ALERT-CRIT-99',
          'identifier': 'SACHET-SYNTH-CYCLONE-01',
          'sender': 'ndma.gov.in',
          'sentAt': '2026-09-20T14:00:00Z',
          'category': 'Met',
          'event': 'Super Cyclonic Storm Alert',
          'urgency': 'Immediate',
          'severity': 'Extreme',
          'certainty': 'Observed',
          'effectiveAt': '2026-09-20T14:00:00Z',
          'expiresAt': '2026-09-20T22:00:00Z',
          'headline': 'Immediate coastal evacuation order',
          'instruction': 'Seek storm shelter inland immediately.',
          'areaDesc': 'Coastal Zone Alpha',
          'isSynthetic': true,
        },
      };

      final result = RiskResult.fromJson(json);

      // Baseline score and risk level are preserved without corruption
      expect(result.safetyScore, 90.0);
      expect(result.riskLevel, 'LOW');
      expect(result.factors.length, 1);
      // Override attributes correctly parsed
      expect(result.overrideActive, true);
      expect(result.effectiveRiskLevel, 'CRITICAL');
      expect(result.activeDisasterAlert, isNotNull);
      expect(result.activeDisasterAlert!.event, 'Super Cyclonic Storm Alert');
      expect(result.activeDisasterAlert!.severity, 'Extreme');
      expect(result.activeDisasterAlert!.isSynthetic, true);
    });

    test('fromJson parses decisionId and dataCompleteness when present', () {
      final json = {
        'decisionId': 'd3b07384-d113-46cf-824b-97e37e96e001',
        'safetyScore': 65.0,
        'riskLevel': 'MEDIUM',
        'recommendation': 'Exercise increased caution.',
        'contributingFactors': [],
        'overrideActive': false,
        'dataCompleteness': {
          'availableFactorCount': 3,
          'expectedLiveFactorCount': 5,
          'availabilityRatio': 0.6,
          'degraded': true,
          'missingFactors': ['SERVICE_PROXIMITY', 'USER_REPORT'],
          'missingReasons': {
            'SERVICE_PROXIMITY': 'Service directory offline',
            'USER_REPORT': 'No recent incidents'
          }
        }
      };

      final result = RiskResult.fromJson(json);

      expect(result.decisionId, 'd3b07384-d113-46cf-824b-97e37e96e001');
      expect(result.dataCompleteness, isNotNull);
      expect(result.dataCompleteness!.availableFactorCount, 3);
      expect(result.dataCompleteness!.expectedLiveFactorCount, 5);
      expect(result.dataCompleteness!.availabilityRatio, 0.6);
      expect(result.dataCompleteness!.degraded, true);
      expect(result.dataCompleteness!.missingFactors, ['SERVICE_PROXIMITY', 'USER_REPORT']);
      expect(result.dataCompleteness!.missingReasons['SERVICE_PROXIMITY'], 'Service directory offline');
    });
  });
}
