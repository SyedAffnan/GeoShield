import '../../../core/network/api_client.dart';
import '../../../core/storage/secure_session_storage.dart';

class Session {
  const Session({required this.role});
  final String role;

  bool get isTourist => role == 'TOURIST';
}

class AuthRepository {
  AuthRepository(this._client, this._storage);
  final GeoShieldApiClient _client;
  final SecureSessionStorage _storage;

  Future<Session?> restoreSession() async {
    final token = await _storage.readAccessToken();
    final role = await _storage.readRole();
    if (token == null || token.isEmpty || role == null || role.isEmpty) {
      return null;
    }
    return Session(role: role);
  }

  Future<Session> login(
      {required String email, required String password}) async {
    final data = await _client.postData('/api/v1/auth/login', data: {
      'email': email,
      'password': password,
    });
    final accessToken = data['accessToken'] as String?;
    final role = data['role'] as String?;
    if (accessToken == null ||
        accessToken.isEmpty ||
        role == null ||
        role.isEmpty) {
      throw StateError('The server returned an incomplete session.');
    }
    await _storage.save(accessToken: accessToken, role: role);
    return Session(role: role);
  }

  Future<void> logout() => _storage.clear();
}
