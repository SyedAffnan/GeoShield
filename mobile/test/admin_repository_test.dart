import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';
import 'package:geoshield_mobile/features/admin/data/admin_repository.dart';

void main() {
  group('AdminRepository', () {
    test('fetchStats parses admin stats correctly', () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'totalUsers': 15,
        'touristCount': 10,
        'responderCount': 3,
        'adminCount': 2,
        'totalIncidents': 5,
        'activeIncidents': 2,
        'resolvedIncidents': 3,
        'totalLocationsRecorded': 100,
        'activeSosAlerts': 1,
      });
      final repository = AdminRepository(_client(interceptor));

      final stats = await repository.fetchStats();

      expect(interceptor.method, 'GET');
      expect(interceptor.path, '/api/v1/admin/stats');
      expect(stats.totalUsers, 15);
      expect(stats.touristCount, 10);
      expect(stats.responderCount, 3);
      expect(stats.adminCount, 2);
      expect(stats.totalIncidents, 5);
      expect(stats.activeIncidents, 2);
      expect(stats.resolvedIncidents, 3);
      expect(stats.totalLocationsRecorded, 100);
      expect(stats.activeSosAlerts, 1);
    });

    test('provisionUser sends valid JSON to provision endpoint', () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'userId': '11111111-1111-1111-1111-111111111111',
        'username': 'newresponder',
        'email': 'resp@test.com',
        'fullName': 'New Responder',
        'phoneNumber': '+919876543210',
        'role': 'RESPONDER',
        'active': true,
      });
      final repository = AdminRepository(_client(interceptor));

      final user = await repository.provisionUser(
        username: 'newresponder',
        email: 'resp@test.com',
        password: 'Password@123',
        fullName: 'New Responder',
        phoneNumber: '+919876543210',
        role: 'RESPONDER',
      );

      expect(interceptor.method, 'POST');
      expect(interceptor.path, '/api/v1/admin/users/provision');
      expect(interceptor.body, {
        'username': 'newresponder',
        'email': 'resp@test.com',
        'password': 'Password@123',
        'fullName': 'New Responder',
        'phoneNumber': '+919876543210',
        'role': 'RESPONDER',
      });
      expect(user.username, 'newresponder');
      expect(user.role, 'RESPONDER');
      expect(user.active, isTrue);
    });

    test('updateUserStatus sends patch request to status endpoint', () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'userId': '11111111-1111-1111-1111-111111111111',
        'username': 'user1',
        'email': 'user1@test.com',
        'fullName': 'User One',
        'phoneNumber': '+919876543210',
        'role': 'TOURIST',
        'active': false,
      });
      final repository = AdminRepository(_client(interceptor));

      final user = await repository.updateUserStatus(
        userId: '11111111-1111-1111-1111-111111111111',
        active: false,
      );

      expect(interceptor.method, 'PATCH');
      expect(interceptor.path, '/api/v1/admin/users/11111111-1111-1111-1111-111111111111/status');
      expect(interceptor.body, {'active': false});
      expect(user.active, isFalse);
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
