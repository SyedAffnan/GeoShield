import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Secure persistence boundary for the authenticated backend session.
abstract interface class SecureSessionStorage {
  Future<String?> readAccessToken();
  Future<String?> readRole();
  Future<void> save({required String accessToken, required String role});
  Future<String?> readBackendBaseUrl();
  Future<void> saveBackendBaseUrl(String baseUrl);
  Future<void> clearBackendBaseUrl();
  Future<void> clear();
}

class FlutterSecureSessionStorage implements SecureSessionStorage {
  FlutterSecureSessionStorage({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const _accessTokenKey = 'geoshield.access_token';
  static const _roleKey = 'geoshield.role';
  static const _backendBaseUrlKey = 'geoshield.backend_base_url';
  final FlutterSecureStorage _storage;

  @override
  Future<String?> readAccessToken() => _storage.read(key: _accessTokenKey);

  @override
  Future<String?> readRole() => _storage.read(key: _roleKey);

  @override
  Future<void> save({required String accessToken, required String role}) async {
    await _storage.write(key: _accessTokenKey, value: accessToken);
    await _storage.write(key: _roleKey, value: role);
  }

  @override
  Future<String?> readBackendBaseUrl() =>
      _storage.read(key: _backendBaseUrlKey);

  @override
  Future<void> saveBackendBaseUrl(String baseUrl) =>
      _storage.write(key: _backendBaseUrlKey, value: baseUrl);

  @override
  Future<void> clearBackendBaseUrl() =>
      _storage.delete(key: _backendBaseUrlKey);

  @override
  Future<void> clear() async {
    await _storage.delete(key: _accessTokenKey);
    await _storage.delete(key: _roleKey);
  }
}
