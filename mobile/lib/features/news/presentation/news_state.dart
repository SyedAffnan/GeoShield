import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/location/device_location_service.dart';
import '../data/news_repository.dart';
import '../data/recent_safety_event_model.dart';

/// Provider for the NewsRepository.
final newsRepositoryProvider = Provider<NewsRepository>((ref) {
  return NewsRepository(ref.watch(apiClientProvider));
});

/// Future provider fetching location-based recent safety news.
final recentNewsProvider =
    FutureProvider.autoDispose<NewsResponseModel>((ref) async {
  final repository = ref.watch(newsRepositoryProvider);

  // Read current position if available without blocking
  final locService = ref.read(deviceLocationServiceProvider);
  final fixResult = await locService.currentPosition();

  double? lat;
  double? lon;
  if (fixResult is DeviceLocationFix) {
    lat = fixResult.latitude;
    lon = fixResult.longitude;
  }

  return repository.fetchRecentSafetyNews(
    latitude: lat,
    longitude: lon,
    limit: 10,
  );
});
