import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';
import 'package:geoshield_mobile/features/sos/data/sos_repository.dart';

void main() {
  group('SosRepository', () {
    test('createSos sends correct POST body and parses response', () async {
      const clientRequestId = '550e8400-e29b-41d4-a716-446655440000';
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': '11111111-1111-1111-1111-111111111111',
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'PENDING',
        'assignedResponderId': null,
        'clientRequestId': clientRequestId,
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final repository = SosRepository(_client(interceptor));

      final alert = await repository.createSos(
        latitude: 12.9716,
        longitude: 77.5946,
        clientRequestId: clientRequestId,
      );

      expect(interceptor.method, 'POST');
      expect(interceptor.path, '/api/v1/sos');
      expect(interceptor.body, {
        'latitude': 12.9716,
        'longitude': 77.5946,
        'clientRequestId': clientRequestId,
      });
      expect(alert.sosId, '11111111-1111-1111-1111-111111111111');
      expect(alert.status, SosStatusValue.pending);
      expect(alert.latitude, closeTo(12.9716, 0.0001));
      expect(alert.longitude, closeTo(77.5946, 0.0001));
    });

    test('getActiveSos returns parsed SosAlert when backend returns active SOS',
        () async {
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': '33333333-3333-3333-3333-333333333333',
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': -33.8688,
        'longitude': 151.2093,
        'status': 'ACKNOWLEDGED',
        'assignedResponderId': null,
        'clientRequestId': '550e8400-e29b-41d4-a716-446655440000',
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final repository = SosRepository(_client(interceptor));

      final alert = await repository.getActiveSos();

      expect(interceptor.method, 'GET');
      expect(interceptor.path, '/api/v1/sos/active');
      expect(alert, isNotNull);
      expect(alert!.sosId, '33333333-3333-3333-3333-333333333333');
      expect(alert.status, SosStatusValue.acknowledged);
    });

    test('getActiveSos returns null when backend returns 404', () async {
      final interceptor = _ErrorInterceptor(statusCode: 404);
      final repository = SosRepository(_client(interceptor));

      final alert = await repository.getActiveSos();

      expect(alert, isNull);
    });

    test('cancelSos sends PATCH request and parses cancelled status', () async {
      const sosId = '44444444-4444-4444-4444-444444444444';
      final interceptor = _CapturingInterceptor(<String, dynamic>{
        'sosId': sosId,
        'userId': '22222222-2222-2222-2222-222222222222',
        'username': 'tourist1',
        'fullName': 'Tourist One',
        'phoneNumber': '+919876543210',
        'latitude': 12.9716,
        'longitude': 77.5946,
        'status': 'CANCELLED',
        'assignedResponderId': null,
        'clientRequestId': '550e8400-e29b-41d4-a716-446655440000',
        'triggeredAt': '2026-08-27T00:00:00Z',
      });
      final repository = SosRepository(_client(interceptor));

      final alert = await repository.cancelSos(sosId);

      expect(interceptor.method, 'PATCH');
      expect(interceptor.path, '/api/v1/sos/$sosId/cancel');
      expect(alert.status, SosStatusValue.cancelled);
      expect(alert.status.isTerminal, isTrue);
      expect(alert.status.isActive, isFalse);
    });
  });

  group('SosStatusValue', () {
    test('all backend status strings parse correctly', () {
      expect(SosStatusValue.fromString('PENDING'), SosStatusValue.pending);
      expect(
          SosStatusValue.fromString('ACKNOWLEDGED'), SosStatusValue.acknowledged);
      expect(SosStatusValue.fromString('RESPONDING'), SosStatusValue.responding);
      expect(SosStatusValue.fromString('RESOLVED'), SosStatusValue.resolved);
      expect(SosStatusValue.fromString('CANCELLED'), SosStatusValue.cancelled);
    });

    test('isActive returns true only for non-terminal statuses', () {
      expect(SosStatusValue.pending.isActive, isTrue);
      expect(SosStatusValue.acknowledged.isActive, isTrue);
      expect(SosStatusValue.responding.isActive, isTrue);
      expect(SosStatusValue.resolved.isActive, isFalse);
      expect(SosStatusValue.cancelled.isActive, isFalse);
    });

    test('isTerminal returns true only for RESOLVED and CANCELLED', () {
      expect(SosStatusValue.pending.isTerminal, isFalse);
      expect(SosStatusValue.acknowledged.isTerminal, isFalse);
      expect(SosStatusValue.responding.isTerminal, isFalse);
      expect(SosStatusValue.resolved.isTerminal, isTrue);
      expect(SosStatusValue.cancelled.isTerminal, isTrue);
    });
  });

  group('generateClientRequestId', () {
    test('produces a valid RFC 4122 UUID v4 string', () {
      final id = generateClientRequestId();
      final uuidRegex = RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          caseSensitive: false);
      expect(uuidRegex.hasMatch(id), isTrue,
          reason: 'Expected a UUID v4 but got: $id');
    });

    test('generates unique IDs on each call', () {
      final ids = List.generate(50, (_) => generateClientRequestId()).toSet();
      expect(ids.length, 50);
    });
  });
}

GeoShieldApiClient _client(Interceptor interceptor) {
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

class _ErrorInterceptor extends Interceptor {
  _ErrorInterceptor({required this.statusCode});
  final int statusCode;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    handler.reject(DioException(
      requestOptions: options,
      response: Response(
        requestOptions: options,
        statusCode: statusCode,
        data: <String, dynamic>{'success': false, 'message': 'Not found'},
      ),
      type: DioExceptionType.badResponse,
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
