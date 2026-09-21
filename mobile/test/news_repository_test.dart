import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';
import 'package:geoshield_mobile/features/news/data/news_repository.dart';

void main() {
  group('NewsRepository.fetchRecentSafetyNews', () {
    test('passes query parameters and parses NewsResponseModel successfully', () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'resolvedArea': 'Shimla',
        'resolutionLevel': 'LOCALITY',
        'retrievedAt': '2026-09-21T10:00:00Z',
        'cached': false,
        'providerAvailable': true,
        'eventsCount': 1,
        'events': [
          {
            'eventId': 'ev-1',
            'title': 'Traffic diversion due to road maintenance',
            'description': 'Maintenance on Mall Road.',
            'sourceName': 'The Tribune',
            'sourceUrl': 'https://tribune.com/mallroad',
            'publishedAt': '2026-09-21T09:00:00Z',
            'category': 'TRAFFIC_AND_TRANSIT',
            'severity': 'LOW',
            'relevance': 'HIGH',
            'areaName': 'Shimla',
            'isVerifiedSource': true,
            'relatedSourcesCount': 1,
          }
        ]
      });

      final repository = NewsRepository(_client(interceptor));

      final response = await repository.fetchRecentSafetyNews(
        latitude: 31.1048,
        longitude: 77.1734,
        locality: 'Mall Road',
        district: 'Shimla',
        category: 'TRAFFIC_AND_TRANSIT',
        limit: 5,
      );

      expect(interceptor.method, 'GET');
      expect(interceptor.path, '/api/v1/news/recent');
      expect(interceptor.queryParams, <String, dynamic>{
        'latitude': 31.1048,
        'longitude': 77.1734,
        'locality': 'Mall Road',
        'district': 'Shimla',
        'category': 'TRAFFIC_AND_TRANSIT',
        'limit': 5,
      });

      expect(response.resolvedArea, 'Shimla');
      expect(response.resolutionLevel, 'LOCALITY');
      expect(response.cached, false);
      expect(response.providerAvailable, true);
      expect(response.events.length, 1);
      expect(response.events.first.id, 'ev-1');
      expect(response.events.first.title, 'Traffic diversion due to road maintenance');
      expect(response.events.first.category, 'TRAFFIC_AND_TRANSIT');
    });

    test('omits optional query parameters when not provided', () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'resolvedArea': 'India',
        'resolutionLevel': 'NATIONAL',
        'retrievedAt': '2026-09-21T10:00:00Z',
        'cached': true,
        'providerAvailable': false,
        'eventsCount': 0,
        'events': <dynamic>[],
      });

      final repository = NewsRepository(_client(interceptor));

      final response = await repository.fetchRecentSafetyNews();

      expect(interceptor.method, 'GET');
      expect(interceptor.path, '/api/v1/news/recent');
      expect(interceptor.queryParams, isNull);
      expect(response.resolvedArea, 'India');
      expect(response.providerAvailable, false);
      expect(response.events, isEmpty);
    });
  });
}

GeoShieldApiClient _client(_CapturingInterceptor interceptor) {
  final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080'));
  dio.interceptors.add(interceptor);
  return GeoShieldApiClient(_NoSessionStorage(), dio: dio);
}

class _CapturingInterceptor extends Interceptor {
  _CapturingInterceptor(this._responseData);
  final dynamic _responseData;

  String? method;
  String? path;
  Map<String, dynamic>? queryParams;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    method = options.method;
    path = options.path;
    queryParams = options.queryParameters.isNotEmpty ? options.queryParameters : null;

    handler.resolve(Response<dynamic>(
      requestOptions: options,
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'News retrieved',
        'data': _responseData,
      },
    ));
  }
}

class _NoSessionStorage implements SecureSessionStorage {
  @override
  Future<String?> readAccessToken() async => null;
  @override
  Future<String?> readRole() async => null;
  @override
  Future<void> save({required String accessToken, required String role}) async {}
  @override
  Future<String?> readBackendBaseUrl() async => null;
  @override
  Future<void> saveBackendBaseUrl(String baseUrl) async {}
  @override
  Future<void> clearBackendBaseUrl() async {}
  @override
  Future<void> clear() async {}
}
