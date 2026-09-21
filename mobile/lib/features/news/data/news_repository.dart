import '../../../core/network/api_client.dart';
import 'recent_safety_event_model.dart';

/// Repository responsible for querying location-based recent safety news.
class NewsRepository {
  NewsRepository(this._apiClient);

  final GeoShieldApiClient _apiClient;

  /// Fetches recent safety news events matching location and optional category.
  Future<NewsResponseModel> fetchRecentSafetyNews({
    double? latitude,
    double? longitude,
    String? locality,
    String? district,
    String? category,
    int? limit,
  }) async {
    final queryParams = <String, dynamic>{};
    if (latitude != null) queryParams['latitude'] = latitude;
    if (longitude != null) queryParams['longitude'] = longitude;
    if (locality != null && locality.trim().isNotEmpty) {
      queryParams['locality'] = locality.trim();
    }
    if (district != null && district.trim().isNotEmpty) {
      queryParams['district'] = district.trim();
    }
    if (category != null && category.trim().isNotEmpty) {
      queryParams['category'] = category.trim();
    }
    if (limit != null && limit > 0) {
      queryParams['limit'] = limit;
    }

    final response = await _apiClient.getData(
      '/api/v1/news/recent',
      queryParameters: queryParams.isNotEmpty ? queryParams : null,
    );

    if (response is Map<String, dynamic>) {
      return NewsResponseModel.fromJson(response);
    } else if (response is Map) {
      return NewsResponseModel.fromJson(Map<String, dynamic>.from(response));
    } else {
      throw const FormatException('Invalid news response format from server');
    }
  }
}
