import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/location/device_location_service.dart';
import '../../../core/location/native_geocoder.dart';
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
  String? locality;
  String? district;
  String? state;
  if (fixResult is DeviceLocationFix) {
    lat = fixResult.latitude;
    lon = fixResult.longitude;

    final geocoded = await NativeGeocoder.reverseGeocode(lat, lon);
    if (geocoded != null) {
      locality = geocoded.bestLocality;
      district = geocoded.district;
      state = geocoded.state;
    }
  }

  return repository.fetchRecentSafetyNews(
    latitude: lat,
    longitude: lon,
    locality: locality,
    district: district,
    state: state,
    limit: 10,
  );
});
