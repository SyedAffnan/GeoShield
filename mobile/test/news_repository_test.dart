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

    test('parses direct unwrapped domain responses without outer data envelope', () async {
      final interceptor = _DirectResponseInterceptor(<String, dynamic>{
        'resolvedArea': 'Tamil Nadu',
        'resolutionLevel': 'STATE_UT',
        'retrievedAt': '2026-09-22T12:00:00Z',
        'cached': false,
        'providerAvailable': true,
        'eventsCount': 1,
        'events': [
          {
            'eventId': 'ev-tn-1',
            'title': '[Test Fixture] Tamil Nadu Highway Advisory',
            'description': 'Monsoon safety advisory for commuters.',
            'sourceName': 'Regional Herald',
            'sourceUrl': 'https://example.com/mock-news/tamil-nadu',
            'publishedAt': '2026-09-22T11:00:00Z',
            'category': 'GENERAL_SAFETY',
            'severity': 'LOW',
            'relevance': 'HIGH',
            'areaName': 'Tamil Nadu',
            'isVerifiedSource': true,
            'relatedSourcesCount': 1,
          }
        ]
      });

      final repository = NewsRepository(_clientDirect(interceptor));
      final response = await repository.fetchRecentSafetyNews();

      expect(response.resolvedArea, 'Tamil Nadu');
      expect(response.providerAvailable, true);
      expect(response.eventsCount, 1);
      expect(response.events.first.title, contains('Tamil Nadu'));
    });
  });
}

GeoShieldApiClient _clientDirect(_DirectResponseInterceptor interceptor) {
  final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080'));
  dio.interceptors.add(interceptor);
  return GeoShieldApiClient(_NoSessionStorage(), dio: dio);
}

class _DirectResponseInterceptor extends Interceptor {
  _DirectResponseInterceptor(this._responseData);
  final dynamic _responseData;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    handler.resolve(Response<dynamic>(
      requestOptions: options,
      statusCode: 200,
      data: _responseData,
    ));
  }
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
