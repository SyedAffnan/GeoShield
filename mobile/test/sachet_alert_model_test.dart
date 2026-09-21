import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/risk/data/sachet_alert_model.dart';

void main() {
  group('SachetAlertModel Tests', () {
    test('fromJson parses full CAP 1.2 alert payload accurately', () {
      final json = {
        'id': 'ALERT-2026-001',
        'identifier': 'NDMA-SACHET-2026-0920-001',
        'sender': 'ndma.gov.in',
        'sentAt': '2026-09-20T10:00:00Z',
        'category': 'Met',
        'event': 'Flash Flood Warning',
        'urgency': 'Immediate',
        'severity': 'Extreme',
        'certainty': 'Observed',
        'effectiveAt': '2026-09-20T10:00:00Z',
        'expiresAt': '2026-09-20T18:00:00Z',
        'headline': 'Immediate evacuation ordered for low-lying river areas',
        'description': 'Severe rainfall has triggered rapid flash flooding.',
        'instruction': 'Move immediately to designated high-ground shelters.',
        'areaDesc': 'Brahmaputra Basin, Sector 4',
        'isSynthetic': false,
      };

      final model = SachetAlertModel.fromJson(json);

      expect(model.id, 'ALERT-2026-001');
      expect(model.identifier, 'NDMA-SACHET-2026-0920-001');
      expect(model.sender, 'ndma.gov.in');
      expect(model.sentAt, DateTime.parse('2026-09-20T10:00:00Z'));
      expect(model.category, 'Met');
      expect(model.event, 'Flash Flood Warning');
      expect(model.urgency, 'Immediate');
      expect(model.severity, 'Extreme');
      expect(model.certainty, 'Observed');
      expect(model.effectiveAt, DateTime.parse('2026-09-20T10:00:00Z'));
      expect(model.expiresAt, DateTime.parse('2026-09-20T18:00:00Z'));
      expect(model.headline,
          'Immediate evacuation ordered for low-lying river areas');
      expect(model.description,
          'Severe rainfall has triggered rapid flash flooding.');
      expect(model.instruction,
          'Move immediately to designated high-ground shelters.');
      expect(model.areaDesc, 'Brahmaputra Basin, Sector 4');
      expect(model.isSynthetic, false);
    });

    test('fromJson handles synthetic flag and missing optional fields safely', () {
      final json = {
        'id': 'SYNTH-99',
        'identifier': 'SYNTH-NDMA-001',
        'sender': 'test.ndma.gov.in',
        'category': 'Safety',
        'event': 'Synthetic Test Drill',
        'urgency': 'Expected',
        'severity': 'Severe',
        'certainty': 'Likely',
        'isSynthetic': true,
      };

      final model = SachetAlertModel.fromJson(json);

      expect(model.id, 'SYNTH-99');
      expect(model.identifier, 'SYNTH-NDMA-001');
      expect(model.sender, 'test.ndma.gov.in');
      expect(model.category, 'Safety');
      expect(model.event, 'Synthetic Test Drill');
      expect(model.urgency, 'Expected');
      expect(model.severity, 'Severe');
      expect(model.certainty, 'Likely');
      expect(model.headline, isNull);
      expect(model.description, isNull);
      expect(model.instruction, isNull);
      expect(model.areaDesc, isNull);
      expect(model.isSynthetic, true);
    });
  });
}
