import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/data/auth_repository.dart';
import '../../features/incidents/data/incident_repository.dart';
import '../../features/risk/data/risk_repository.dart';
import '../network/api_client.dart';
import '../storage/secure_session_storage.dart';

final secureSessionStorageProvider = Provider<SecureSessionStorage>((ref) {
  return FlutterSecureSessionStorage();
});

final apiClientProvider = Provider<GeoShieldApiClient>((ref) {
  return GeoShieldApiClient(ref.watch(secureSessionStorageProvider));
});

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
      ref.watch(apiClientProvider), ref.watch(secureSessionStorageProvider));
});

final authControllerProvider =
    AsyncNotifierProvider<AuthController, Session?>(AuthController.new);

class AuthController extends AsyncNotifier<Session?> {
  @override
  FutureOr<Session?> build() =>
      ref.read(authRepositoryProvider).restoreSession();

  Future<Session> login(
      {required String email, required String password}) async {
    state = const AsyncLoading();
    try {
      final session = await ref
          .read(authRepositoryProvider)
          .login(email: email, password: password);
      state = AsyncData(session);
      return session;
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
      rethrow;
    }
  }

  Future<void> logout() async {
    await ref.read(authRepositoryProvider).logout();
    state = const AsyncData(null);
  }
}

final riskRepositoryProvider = Provider<RiskRepository>((ref) {
  return RiskRepository(ref.watch(apiClientProvider));
});

final riskDashboardProvider =
    FutureProvider.autoDispose<RiskDashboardData>((ref) {
  return ref.watch(riskRepositoryProvider).loadDashboard();
});

final incidentRepositoryProvider = Provider<IncidentRepository>((ref) {
  return IncidentRepository(ref.watch(apiClientProvider));
});

final incidentsProvider = FutureProvider.autoDispose<List<Incident>>((ref) {
  return ref.watch(incidentRepositoryProvider).getIncidents();
});
