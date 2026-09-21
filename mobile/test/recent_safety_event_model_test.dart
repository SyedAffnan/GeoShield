import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/news/data/recent_safety_event_model.dart';

void main() {
  group('RecentSafetyEventModel Tests', () {
    test('fromJson parses full event payload accurately', () {
      final json = {
        'eventId': 'e8a719d4-6330-47b2-a42e-ec5f25a9b831',
        'eventGroupId': 'grp-1234',
        'title': 'Landslide blocks highway near Shimla',
        'description': 'Heavy rockfall halts vehicular traffic.',
        'sourceName': 'The Hindu',
        'sourceUrl': 'https://thehindu.com/news/national/landslide-shimla',
        'imageUrl': 'https://thehindu.com/images/slide.jpg',
        'publishedAt': '2026-09-21T08:00:00Z',
        'retrievedAt': '2026-09-21T09:00:00Z',
        'category': 'NATURAL_DISASTER',
        'severity': 'HIGH',
        'relevance': 'HIGH',
        'areaName': 'Shimla',
        'isVerifiedSource': true,
        'relatedSourcesCount': 3,
      };

      final model = RecentSafetyEventModel.fromJson(json);

      expect(model.id, 'e8a719d4-6330-47b2-a42e-ec5f25a9b831');
      expect(model.eventGroupId, 'grp-1234');
      expect(model.title, 'Landslide blocks highway near Shimla');
      expect(model.sourceName, 'The Hindu');
      expect(model.sourceUrl, 'https://thehindu.com/news/national/landslide-shimla');
      expect(model.category, 'NATURAL_DISASTER');
      expect(model.severity, 'HIGH');
      expect(model.relevance, 'HIGH');
      expect(model.areaName, 'Shimla');
      expect(model.isVerifiedSource, true);
      expect(model.relatedSourcesCount, 3);
    });

    test('NewsResponseModel parses response envelope and lists events', () {
      final json = {
        'resolvedArea': 'Shimla',
        'resolutionLevel': 'LOCALITY',
        'retrievedAt': '2026-09-21T09:00:00Z',
        'cached': true,
        'providerAvailable': true,
        'eventsCount': 1,
        'events': [
          {
            'eventId': '111',
            'title': 'Accident on bypass',
            'description': 'Two cars collided.',
            'sourceName': 'Tribune',
            'sourceUrl': 'https://tribune.com/1',
            'publishedAt': '2026-09-21T07:30:00Z',
            'category': 'TRAFFIC_AND_TRANSIT',
            'severity': 'MODERATE',
            'relevance': 'HIGH',
            'areaName': 'Shimla',
            'isVerifiedSource': false,
            'relatedSourcesCount': 1,
          }
        ]
      };

      final response = NewsResponseModel.fromJson(json);

      expect(response.resolvedArea, 'Shimla');
      expect(response.resolutionLevel, 'LOCALITY');
      expect(response.cached, true);
      expect(response.providerAvailable, true);
      expect(response.eventsCount, 1);
      expect(response.events.length, 1);
      expect(response.events.first.category, 'TRAFFIC_AND_TRANSIT');
    });

    test('Handles missing optional fields safely with sensible defaults', () {
      final json = <String, dynamic>{};
      final response = NewsResponseModel.fromJson(json);

      expect(response.resolvedArea, 'India');
      expect(response.resolutionLevel, 'NATIONAL');
      expect(response.providerAvailable, false);
      expect(response.events, isEmpty);
    });
  });
}
