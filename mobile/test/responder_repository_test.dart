import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';
import 'package:geoshield_mobile/features/responder/data/responder_repository.dart';

void main() {
  group('ResponderRepository', () {
    test('fetchIncidentQueue parses incident list correctly', () async {
      final interceptor = _CapturingInterceptor(<dynamic>[
        {
          'incidentId': '22222222-2222-2222-2222-222222222222',
          'reporterId': '33333333-3333-3333-3333-333333333333',
          'reporterUsername': 'tourist1',
          'reporterFullName': 'Tourist One',
          'reporterPhoneNumber': '+919876543210',
          'incidentType': 'THEFT',
          'description': 'Bag stolen at beach',
          'latitude': 12.9716,
          'longitude': 77.5946,
          'status': 'REPORTED',
          'createdAt': '2026-08-26T10:00:00Z',
        }
      ]);
      final repository = ResponderRepository(_client(interceptor));

      final queue = await repository.fetchIncidentQueue();

      expect(interceptor.method, 'GET');
      expect(interceptor.path, '/api/v1/responder/incidents/queue');
      expect(queue.length, 1);
      expect(queue.first.incidentType, 'THEFT');
      expect(queue.first.status, 'REPORTED');
      expect(queue.first.reporterUsername, 'tourist1');
    });

    test('updateIncidentStatus sends PATCH request with new status', () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'incidentId': '22222222-2222-2222-2222-222222222222',
        'reporterId': '33333333-3333-3333-3333-333333333333',
        'reporterUsername': 'tourist1',
        'reporterFullName': 'Tourist One',
        'reporterPhoneNumber': '+919876543210',
        'incidentType': 'THEFT',
        'description': 'Bag stolen at beach',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'ACKNOWLEDGED',
      });
      final repository = ResponderRepository(_client(interceptor));

      final result = await repository.updateIncidentStatus(
        '22222222-2222-2222-2222-222222222222',
        'ACKNOWLEDGED',
      );

      expect(interceptor.method, 'PATCH');
      expect(interceptor.path,
          '/api/v1/responder/incidents/22222222-2222-2222-2222-222222222222/status');
      expect(interceptor.body, {'status': 'ACKNOWLEDGED'});
      expect(result.status, 'ACKNOWLEDGED');
    });

    test('fetchSosQueue parses SOS items correctly', () async {
      final interceptor = _CapturingInterceptor(<dynamic>[
        {
          'sosId': '44444444-4444-4444-4444-444444444444',
          'userId': '33333333-3333-3333-3333-333333333333',
          'username': 'tourist1',
          'fullName': 'Tourist One',
          'phoneNumber': '+919876543210',
          'latitude': 12.9716,
          'longitude': 77.5946,
          'status': 'PENDING',
          'triggeredAt': '2026-08-26T10:00:00Z',
        }
      ]);
      final repository = ResponderRepository(_client(interceptor));

      final queue = await repository.fetchSosQueue();

      expect(interceptor.method, 'GET');
      expect(interceptor.path, '/api/v1/sos/queue');
      expect(queue.length, 1);
      expect(queue.first.status, 'PENDING');
      expect(queue.first.username, 'tourist1');
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
        'message': 'OK',
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
