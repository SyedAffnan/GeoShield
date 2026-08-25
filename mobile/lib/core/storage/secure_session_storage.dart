import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Secure persistence boundary for the authenticated backend session.
abstract interface class SecureSessionStorage {
  Future<String?> readAccessToken();
  Future<String?> readRole();
  Future<void> save({required String accessToken, required String role});
  Future<void> clear();
}

class FlutterSecureSessionStorage implements SecureSessionStorage {
  FlutterSecureSessionStorage({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const _accessTokenKey = 'geoshield.access_token';
  static const _roleKey = 'geoshield.role';
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
  Future<void> clear() => _storage.deleteAll();
}
