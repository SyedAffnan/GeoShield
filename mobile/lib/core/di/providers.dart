import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/admin/data/admin_repository.dart';
import '../../features/auth/data/auth_repository.dart';
import '../../features/incidents/data/incident_repository.dart';
import '../../features/location/data/location_repository.dart';
import '../../features/responder/data/responder_repository.dart';
import '../../features/risk/data/historical_advisory_model.dart';
import '../../features/risk/data/risk_repository.dart';
import '../../features/sos/data/sos_outbox_store.dart';
import '../../features/sos/data/sos_repository.dart';
import '../geofencing/presentation/geofence_controller.dart';
import '../geofencing/services/geofence_notification_service.dart';
import '../geofencing/services/geofencing_engine.dart';
import '../geofencing/services/hazard_geometry_provider.dart';
import '../location/device_location_service.dart';
import '../network/api_client.dart';
import '../network/api_configuration.dart';
import '../network/auth_exception.dart';
import '../network/connectivity_service.dart';
import '../network/connectivity_state.dart';
import '../network/validation_exception.dart';
import '../storage/secure_session_storage.dart';

final secureSessionStorageProvider = Provider<SecureSessionStorage>((ref) {
  return FlutterSecureSessionStorage();
});

final apiClientProvider = Provider<GeoShieldApiClient>((ref) {
  return GeoShieldApiClient(ref.watch(secureSessionStorageProvider));
});

final connectivityServiceProvider = Provider<ConnectivityService>((ref) {
  final apiClient = ref.watch(apiClientProvider);
  final service = ConnectivityService(
    baseUrl: apiClient.baseUrl,
  );
  ref.onDispose(service.dispose);
  return service;
});

final connectivityStateProvider = StreamProvider<ConnectivityState>((ref) async* {
  final service = ref.watch(connectivityServiceProvider);
  yield service.state;
  yield* service.stateStream;
});

final apiConfigurationProvider =
    AsyncNotifierProvider<ApiConfigurationController, ApiConfiguration>(
  ApiConfigurationController.new,
);

/// Runtime API-origin configuration. A saved value takes precedence over dart-define.
class ApiConfigurationController extends AsyncNotifier<ApiConfiguration> {
  @override
  FutureOr<ApiConfiguration> build() async {
    final storage = ref.read(secureSessionStorageProvider);
    final savedUrl = await storage.readBackendBaseUrl();
    final effectiveUrl = savedUrl == null || savedUrl.isEmpty
        ? GeoShieldApiClient.defaultBaseUrl
        : GeoShieldApiClient.normalizeBaseUrl(savedUrl);
    ref.read(apiClientProvider).setBaseUrl(effectiveUrl);
    return ApiConfiguration(
      effectiveBaseUrl: effectiveUrl,
      customBaseUrl: (savedUrl?.isEmpty ?? true) ? null : effectiveUrl,
    );
  }

  Future<void> saveCustomBaseUrl(String value) async {
    final normalized = GeoShieldApiClient.normalizeBaseUrl(value);
    await ref.read(secureSessionStorageProvider).saveBackendBaseUrl(normalized);
    ref.read(apiClientProvider).setBaseUrl(normalized);
    state = AsyncData(ApiConfiguration(
      effectiveBaseUrl: normalized,
      customBaseUrl: normalized,
    ));
  }

  Future<void> useDefault() async {
    await ref.read(secureSessionStorageProvider).clearBackendBaseUrl();
    ref.read(apiClientProvider).setBaseUrl(GeoShieldApiClient.defaultBaseUrl);
    state = const AsyncData(ApiConfiguration(
      effectiveBaseUrl: GeoShieldApiClient.defaultBaseUrl,
    ));
  }
}

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

final deviceLocationServiceProvider = Provider<DeviceLocationService>((ref) {
  return const GeolocatorDeviceLocationService();
});

final locationRepositoryProvider = Provider<LocationRepository>((ref) {
  return LocationRepository(ref.watch(apiClientProvider));
});

final riskRepositoryProvider = Provider<RiskRepository>((ref) {
  return RiskRepository(ref.watch(apiClientProvider));
});

final historicalTrendAdvisoryProvider =
    FutureProvider.autoDispose<HistoricalTrendAdvisoryModel>((ref) async {
  final repo = ref.watch(riskRepositoryProvider);
  return repo.getHistoricalTrendAdvisory();
});

final hazardGeometryProvider = Provider<HazardGeometryProvider>((ref) {
  if (!kDebugMode) {
    return const EmptyProductionHazardProvider();
  }
  return SyntheticTestHazardProvider();
});

final geofenceNotificationServiceProvider =
    Provider<GeofenceNotificationService>((ref) {
  return GeofenceNotificationService();
});

final geofencingEngineProvider = Provider<GeofencingEngine>((ref) {
  return GeofencingEngine(
    hazardProvider: ref.watch(hazardGeometryProvider),
  );
});

final geofenceControllerProvider =
    NotifierProvider<GeofenceController, GeofenceState>(
  GeofenceController.new,
);

final riskDashboardProvider = NotifierProvider.autoDispose<
    RiskDashboardController, RiskDashboardState>(RiskDashboardController.new);

/// Drives phone GPS -> `POST /api/v1/locations` -> `GET /api/v1/risk`. Every value
/// shown on the dashboard comes from the backend; nothing is scored on the device.
class RiskDashboardController extends AutoDisposeNotifier<RiskDashboardState> {
  bool _disposed = false;

  @override
  RiskDashboardState build() {
    ref.onDispose(() => _disposed = true);
    Future.microtask(refresh);
    return const RiskDashboardBusy(RiskDashboardStep.obtainingLocation);
  }

  Future<void> refresh() async {
    unawaited(ref.read(connectivityServiceProvider).recheck());
    _emit(const RiskDashboardBusy(RiskDashboardStep.obtainingLocation));
    final result =
        await ref.read(deviceLocationServiceProvider).currentPosition();
    if (result is DeviceLocationFailure) {
      ref
          .read(geofenceControllerProvider.notifier)
          .setUnavailable('Location service unavailable');
      _emit(RiskDashboardLocationBlocked(result.reason));
      return;
    }
    final fix = result as DeviceLocationFix;
    unawaited(
        ref.read(geofenceControllerProvider.notifier).processLocationFix(fix));
    try {
      _emit(const RiskDashboardBusy(RiskDashboardStep.sendingLocation));
      final storedLocation =
          await ref.read(locationRepositoryProvider).submitCurrentLocation(fix);
      _emit(const RiskDashboardBusy(RiskDashboardStep.loadingRisk));
      final risk = await ref.read(riskRepositoryProvider).getCurrentRisk();
      _emit(RiskDashboardReady(RiskDashboardData(
          fix: fix, storedLocation: storedLocation, risk: risk)));
    } on AuthException {
      _emit(const RiskDashboardFailed(RiskDashboardFailureKind.session));
    } on ValidationException catch (error) {
      _emit(RiskDashboardFailed(RiskDashboardFailureKind.serverRejected,
          message: error.message));
    } catch (_) {
      _emit(const RiskDashboardFailed(RiskDashboardFailureKind.network));
    }
  }

  void _emit(RiskDashboardState next) {
    if (!_disposed) state = next;
  }
}

final incidentRepositoryProvider = Provider<IncidentRepository>((ref) {
  return IncidentRepository(ref.watch(apiClientProvider));
});

final incidentsProvider = FutureProvider.autoDispose<List<Incident>>((ref) {
  return ref.watch(incidentRepositoryProvider).getIncidents();
});

final adminRepositoryProvider = Provider<AdminRepository>((ref) {
  return AdminRepository(ref.watch(apiClientProvider));
});

final responderRepositoryProvider = Provider<ResponderRepository>((ref) {
  return ResponderRepository(ref.watch(apiClientProvider));
});

final sosOutboxStoreProvider = Provider<SosOutboxStore>((ref) {
  final store = SqfliteSosOutboxStore();
  unawaited(store.init());
  return store;
});

final sosRepositoryProvider = Provider<SosRepository>((ref) {
  final store = ref.watch(sosOutboxStoreProvider);
  final repo = SosRepository(
    ref.watch(apiClientProvider),
    outboxStore: store,
  );
  ref.listen<AsyncValue<ConnectivityState>>(connectivityStateProvider, (prev, next) {
    if (next.asData?.value.backendReachable == true) {
      unawaited(repo.drainOutbox());
    }
  });
  return repo;
});
