import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';

class _FakeSessionStorage implements SecureSessionStorage {
  @override
  Future<void> clear() async {}
  @override
  Future<void> clearBackendBaseUrl() async {}
  @override
  Future<String?> readAccessToken() async => null;
  @override
  Future<String?> readBackendBaseUrl() async => null;
  @override
  Future<String?> readRole() async => null;
  @override
  Future<void> save({required String accessToken, required String role}) async {}
  @override
  Future<void> saveBackendBaseUrl(String baseUrl) async {}
}

void main() {
  group('GeoShieldApiClient.normalizeBaseUrl', () {
    test('retains a valid origin and removes a trailing slash', () {
      expect(
        GeoShieldApiClient.normalizeBaseUrl('http://192.168.0.13:8080/', requireHttps: false),
        'http://192.168.0.13:8080',
      );
    });

    test('accepts HTTPS origins', () {
      expect(
        GeoShieldApiClient.normalizeBaseUrl('https://example.org'),
        'https://example.org',
      );
    });

    test('enforces HTTPS for remote production hosts when requireHttps is true', () {
      expect(
        () => GeoShieldApiClient.normalizeBaseUrl('http://api.geoshield.com', requireHttps: true),
        throwsFormatException,
      );
      expect(
        GeoShieldApiClient.normalizeBaseUrl('https://api.geoshield.com', requireHttps: true),
        'https://api.geoshield.com',
      );
    });

    test('permits local development loopback origins over cleartext HTTP even when requireHttps is true', () {
      expect(
        GeoShieldApiClient.normalizeBaseUrl('http://10.0.2.2:8080', requireHttps: true),
        'http://10.0.2.2:8080',
      );
      expect(
        GeoShieldApiClient.normalizeBaseUrl('http://localhost:8080', requireHttps: true),
        'http://localhost:8080',
      );
      expect(
        GeoShieldApiClient.normalizeBaseUrl('http://127.0.0.1:8080', requireHttps: true),
        'http://127.0.0.1:8080',
      );
    });

    test('rejects a URL containing an API path', () {
      expect(
        () => GeoShieldApiClient.normalizeBaseUrl(
          'http://192.168.0.13:8080/api/v1',
          requireHttps: false,
        ),
        throwsFormatException,
      );
    });

    test('rejects unsupported schemes and query parameters', () {
      expect(
        () => GeoShieldApiClient.normalizeBaseUrl('ftp://example.org'),
        throwsFormatException,
      );
      expect(
        () => GeoShieldApiClient.normalizeBaseUrl('http://example.org?x=1'),
        throwsFormatException,
      );
    });
  });

  group('GeoShieldApiClient timeout reliability', () {
    test('configures bounded network timeouts on Dio options', () {
      final client = GeoShieldApiClient(_FakeSessionStorage());
      expect(client.dio.options.connectTimeout, const Duration(seconds: 10));
      expect(client.dio.options.receiveTimeout, const Duration(seconds: 15));
      expect(client.dio.options.sendTimeout, const Duration(seconds: 10));
    });
  });
}
