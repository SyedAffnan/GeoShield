import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/location/device_location_service.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';
import 'package:geoshield_mobile/features/location/data/location_repository.dart';

void main() {
  group('LocationRepository.submitCurrentLocation', () {
    test('posts the real device fix to the existing locations endpoint', () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'locationId': 7,
        'latitude': 12.9715987,
        'longitude': 77.5945627,
        'accuracy': 8.5,
        'speed': 0.0,
        'timestamp': '2026-08-26T10:15:30Z',
      });
      final repository = LocationRepository(_client(interceptor));

      final stored = await repository.submitCurrentLocation(DeviceLocationFix(
        latitude: 12.9715987,
        longitude: 77.5945627,
        accuracy: 8.5,
        speed: 0,
        timestamp: DateTime.utc(2026, 8, 26, 10, 15, 30),
      ));

      expect(interceptor.method, 'POST');
      expect(interceptor.path, '/api/v1/locations');
      expect(interceptor.body, <String, dynamic>{
        'latitude': 12.9715987,
        'longitude': 77.5945627,
        'accuracy': 8.5,
        'speed': 0.0,
        'timestamp': '2026-08-26T10:15:30.000Z',
      });
      expect(stored.latitude, 12.9715987);
      expect(stored.longitude, 77.5945627);
    });

    test('omits accuracy and speed when the platform reports neither', () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'locationId': 8,
        'latitude': -33.8688,
        'longitude': 151.2093,
        'accuracy': null,
        'speed': null,
        'timestamp': '2026-08-26T10:20:00Z',
      });
      final repository = LocationRepository(_client(interceptor));

      await repository.submitCurrentLocation(DeviceLocationFix(
        latitude: -33.8688,
        longitude: 151.2093,
        accuracy: null,
        speed: null,
        timestamp: DateTime.utc(2026, 8, 26, 10, 20),
      ));

      expect(interceptor.body!.containsKey('accuracy'), isFalse);
      expect(interceptor.body!.containsKey('speed'), isFalse);
    });
  });
}

GeoShieldApiClient _client(_CapturingInterceptor interceptor) {
  final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080'));
  dio.interceptors.add(interceptor);
  return GeoShieldApiClient(_NoSessionStorage(), dio: dio);
}

/// Resolves every request locally, so the request body can be asserted without a server.
class _CapturingInterceptor extends Interceptor {
  _CapturingInterceptor(this._responseData);
  final Map<String, dynamic> _responseData;

  String? method;
  String? path;
  Map<String, dynamic>? body;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    method = options.method;
    path = options.path;
    final data = options.data;
    body = data is Map<String, dynamic> ? data : null;
    handler.resolve(Response<dynamic>(
      requestOptions: options,
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'Location updated',
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
