import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/features/news/data/recent_safety_event_model.dart';
import 'package:geoshield_mobile/features/news/presentation/news_state.dart';
import 'package:geoshield_mobile/features/news/presentation/recent_safety_events_card.dart';

void main() {
  Widget buildTestCard({
    required AsyncValue<NewsResponseModel> newsValue,
  }) {
    return ProviderScope(
      overrides: [
        recentNewsProvider.overrideWith((ref) => newsValue.when(
              data: (data) => Future.value(data),
              error: (err, stack) => Future.error(err, stack),
              loading: () => Completer<NewsResponseModel>().future,
            )),
      ],
      child: const MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: RecentSafetyEventsCard(),
            ),
          ),
        ),
      ),
    );
  }

  group('RecentSafetyEventsCard Widget Tests', () {
    testWidgets('Renders loading indicator when fetching news', (tester) async {
      await tester.pumpWidget(
        buildTestCard(newsValue: const AsyncValue.loading()),
      );

      expect(find.text('Recent Safety News'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('Renders error state when news fetch fails', (tester) async {
      await tester.pumpWidget(
        buildTestCard(
          newsValue: AsyncValue.error(
            Exception('Network failed'),
            StackTrace.empty,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Recent Safety News'), findsOneWidget);
      expect(
        find.text('Unable to load local safety news at this time.'),
        findsOneWidget,
      );
      expect(find.byIcon(Icons.refresh), findsOneWidget);
    });

    testWidgets('Renders provider unavailable state', (tester) async {
      final response = NewsResponseModel(
        resolvedArea: 'Shimla',
        resolutionLevel: 'LOCALITY',
        retrievedAt: DateTime.parse('2026-09-21T10:00:00Z'),
        cached: false,
        providerAvailable: false,
        eventsCount: 0,
        events: const [],
      );

      await tester.pumpWidget(
        buildTestCard(newsValue: AsyncValue.data(response)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Recent Safety News'), findsOneWidget);
      expect(
        find.text('Regional news feed currently unavailable.'),
        findsOneWidget,
      );
      expect(find.text('Near Shimla'), findsOneWidget);
    });

    testWidgets('Renders empty state when 0 events reported in area', (tester) async {
      final response = NewsResponseModel(
        resolvedArea: 'Shimla',
        resolutionLevel: 'LOCALITY',
        retrievedAt: DateTime.parse('2026-09-21T10:00:00Z'),
        cached: false,
        providerAvailable: true,
        eventsCount: 0,
        events: const [],
      );

      await tester.pumpWidget(
        buildTestCard(newsValue: AsyncValue.data(response)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Recent Safety News'), findsOneWidget);
      expect(
        find.text('No recent safety incidents reported in Shimla in the past 72 hours.'),
        findsOneWidget,
      );
      expect(
        find.text('Local safety reports near Shimla'),
        findsOneWidget,
      );
    });

    testWidgets('Renders events list and tapping event displays details dialog', (tester) async {
      final event = RecentSafetyEventModel(
        id: 'ev-99',
        eventGroupId: 'grp-99',
        title: 'Landslide clears near Solan bypass',
        description: 'Road workers clear minor landslide debris.',
        sourceName: 'Himachal Express',
        sourceUrl: 'https://himachalexpress.test/landslide',
        publishedAt: DateTime.now().subtract(const Duration(minutes: 30)),
        retrievedAt: DateTime.now(),
        category: 'NATURAL_DISASTER',
        severity: 'HIGH',
        relevance: 'HIGH',
        areaName: 'Solan',
        isVerifiedSource: true,
        relatedSourcesCount: 2,
      );

      final response = NewsResponseModel(
        resolvedArea: 'Solan',
        resolutionLevel: 'DISTRICT',
        retrievedAt: DateTime.now(),
        cached: true,
        providerAvailable: true,
        eventsCount: 1,
        events: [event],
      );

      await tester.pumpWidget(
        buildTestCard(newsValue: AsyncValue.data(response)),
      );
      await tester.pumpAndSettle();

      // Verify card content
      expect(find.text('Recent Safety News'), findsOneWidget);
      expect(find.text('Solan District'), findsOneWidget);
      expect(find.text('Landslide clears near Solan bypass'), findsOneWidget);
      expect(find.text('Himachal Express'), findsOneWidget);
      expect(find.text('HIGH'), findsOneWidget);
      expect(find.text('+1 more'), findsOneWidget);
      expect(find.text('District safety reports for Solan'), findsOneWidget);

      // Tap on event tile to open details dialog
      await tester.tap(find.text('Landslide clears near Solan bypass'));
      await tester.pumpAndSettle();

      // Verify dialog content
      expect(find.text('NATURAL DISASTER'), findsOneWidget);
      expect(
        find.text('Road workers clear minor landslide debris.'),
        findsOneWidget,
      );
      expect(
        find.text('Third-party regional news report. Not an internally verified GeoShield incident.'),
        findsOneWidget,
      );
      expect(find.text('Close'), findsOneWidget);

      // Dismiss dialog
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();

      expect(find.text('Third-party regional news report. Not an internally verified GeoShield incident.'), findsNothing);
    });
  });
}
