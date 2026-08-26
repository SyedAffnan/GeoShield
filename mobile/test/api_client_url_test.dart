import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';

void main() {
  group('GeoShieldApiClient.normalizeBaseUrl', () {
    test('retains a valid origin and removes a trailing slash', () {
      expect(
        GeoShieldApiClient.normalizeBaseUrl('http://192.168.0.13:8080/'),
        'http://192.168.0.13:8080',
      );
    });

    test('accepts HTTPS origins', () {
      expect(
        GeoShieldApiClient.normalizeBaseUrl('https://example.org'),
        'https://example.org',
      );
    });

    test('rejects a URL containing an API path', () {
      expect(
        () => GeoShieldApiClient.normalizeBaseUrl(
          'http://192.168.0.13:8080/api/v1',
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
}
