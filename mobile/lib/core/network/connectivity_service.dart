import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:dio/dio.dart';

import 'connectivity_state.dart';

typedef HealthProbeFn = Future<({bool reachable, int? rttMs, String? error})>
    Function();

/// Service providing runtime visibility into network connectivity and GeoShield backend reachability.
///
/// Strictly OPERATIONAL telemetry. Never feeds into physical risk scoring.
class ConnectivityService {
  ConnectivityService({
    Connectivity? connectivity,
    Dio? dio,
    String? baseUrl,
    HealthProbeFn? probeFn,
    Duration periodicInterval = const Duration(seconds: 30),
    DateTime Function()? nowFn,
    bool autoStart = true,
  })  : _connectivity = connectivity ?? Connectivity(),
        _dio = dio,
        _baseUrl = baseUrl ?? 'http://10.0.2.2:8080',
        _customProbeFn = probeFn,
        _periodicInterval = periodicInterval,
        _nowFn = nowFn {
    if (autoStart) {
      initialize();
    }
  }

  final Connectivity _connectivity;
  final Dio? _dio;
  final String _baseUrl;
  final HealthProbeFn? _customProbeFn;
  final Duration _periodicInterval;
  final DateTime Function()? _nowFn;

  final _stateController = StreamController<ConnectivityState>.broadcast();
  Stream<ConnectivityState> get stateStream => _stateController.stream;

  ConnectivityState _state = ConnectivityState.initial();
  ConnectivityState get state => _state;

  StreamSubscription<List<ConnectivityResult>>? _connectivitySubscription;
  Timer? _periodicTimer;

  DateTime? _lossStartedAt;
  Future<ConnectivityState>? _activeProbeFuture;
  bool _isDisposed = false;

  DateTime _now() => _nowFn?.call() ?? DateTime.now();

  void initialize() {
    if (_isDisposed) return;

    // 1. Listen for connectivity interface transitions
    _connectivitySubscription = _connectivity.onConnectivityChanged.listen(
      _onConnectivityChanged,
      onError: (Object error) {
        _updateState(_state.copyWith(
          networkStatus: NetworkStatus.unknown,
          status: OperationalConnectivityStatus.unknown,
          backendReachable: false,
          clearRtt: true,
          measuredAt: _now(),
          unreachableReason: error.toString(),
        ));
      },
    );

    // 2. Perform immediate startup check without waiting for future events
    _initialCheck();

    // 3. Foreground periodic reachability re-check (TO-BE-VALIDATED 30s engineering parameter)
    _startPeriodicTimer();
  }

  Future<void> _initialCheck() async {
    try {
      final results = await _connectivity.checkConnectivity();
      await _handleConnectivityResults(results);
    } catch (e) {
      _updateState(_state.copyWith(
        networkStatus: NetworkStatus.unknown,
        status: OperationalConnectivityStatus.unknown,
        backendReachable: false,
        clearRtt: true,
        measuredAt: _now(),
        unreachableReason: e.toString(),
      ));
    }
  }

  void _onConnectivityChanged(List<ConnectivityResult> results) {
    if (_isDisposed) return;
    _handleConnectivityResults(results);
  }

  Future<void> _handleConnectivityResults(
      List<ConnectivityResult> results) async {
    if (_isDisposed) return;

    final isDeviceOnline = results.isNotEmpty &&
        results.any((r) => r != ConnectivityResult.none);

    final now = _now();

    if (!isDeviceOnline) {
      _lossStartedAt ??= now;
      final lossDuration = now.difference(_lossStartedAt!);
      _updateState(_state.copyWith(
        networkStatus: NetworkStatus.offline,
        status: OperationalConnectivityStatus.offline,
        backendReachable: false,
        clearRtt: true,
        connectivityLossDuration:
            lossDuration.isNegative ? Duration.zero : lossDuration,
        measuredAt: now,
        unreachableReason: 'No network connection',
      ));
    } else {
      _updateState(_state.copyWith(
        networkStatus: NetworkStatus.online,
        measuredAt: now,
      ));
      await probeBackend();
    }
  }

  /// Manual refresh / recheck entry point (e.g. called on dashboard pull-to-refresh).
  Future<ConnectivityState> recheck() async {
    if (_isDisposed) return _state;
    try {
      final results = await _connectivity.checkConnectivity();
      await _handleConnectivityResults(results);
      return _state;
    } catch (e) {
      return probeBackend();
    }
  }

  /// Probes GeoShield backend HTTP liveness/reachability endpoint (/api/v1/health).
  ///
  /// Enforces a SINGLE IN-FLIGHT PROBE GUARD to prevent overlapping probes
  /// when triggered concurrently by network events, manual refresh, or periodic timer.
  Future<ConnectivityState> probeBackend() {
    if (_isDisposed) return Future.value(_state);

    // If device is confirmed offline, avoid pointless probe
    if (_state.networkStatus == NetworkStatus.offline) {
      final now = _now();
      _lossStartedAt ??= now;
      final lossDuration = now.difference(_lossStartedAt!);
      _updateState(_state.copyWith(
        status: OperationalConnectivityStatus.offline,
        backendReachable: false,
        clearRtt: true,
        connectivityLossDuration:
            lossDuration.isNegative ? Duration.zero : lossDuration,
        measuredAt: now,
      ));
      return Future.value(_state);
    }

    // Single in-flight probe coalescing
    if (_activeProbeFuture != null) {
      return _activeProbeFuture!;
    }

    final probeFuture = _runProbe();
    _activeProbeFuture = probeFuture;
    return probeFuture;
  }

  Future<ConnectivityState> _runProbe() async {
    try {
      final probeResult = await _executeProbe();
      if (_isDisposed) return _state;

      final now = _now();
      if (probeResult.reachable) {
        _lossStartedAt = null;
        _updateState(_state.copyWith(
          networkStatus: NetworkStatus.online,
          status: OperationalConnectivityStatus.connectedReachable,
          backendReachable: true,
          backendRttMs: probeResult.rttMs,
          connectivityLossDuration: Duration.zero,
          measuredAt: now,
          clearReason: true,
        ));
      } else {
        _lossStartedAt ??= now;
        final lossDuration = now.difference(_lossStartedAt!);
        _updateState(_state.copyWith(
          networkStatus: _state.networkStatus == NetworkStatus.offline
              ? NetworkStatus.offline
              : NetworkStatus.online,
          status: _state.networkStatus == NetworkStatus.offline
              ? OperationalConnectivityStatus.offline
              : OperationalConnectivityStatus.connectedBackendUnreachable,
          backendReachable: false,
          clearRtt: true,
          connectivityLossDuration:
              lossDuration.isNegative ? Duration.zero : lossDuration,
          measuredAt: now,
          unreachableReason: probeResult.error,
        ));
      }
      return _state;
    } finally {
      _activeProbeFuture = null;
    }
  }

  Future<({bool reachable, int? rttMs, String? error})> _executeProbe() async {
    final probeFn = _customProbeFn;
    if (probeFn != null) {
      return await probeFn();
    }

    final client = _dio ??
        Dio(
          BaseOptions(
            connectTimeout: const Duration(seconds: 3),
            receiveTimeout: const Duration(seconds: 3),
            sendTimeout: const Duration(seconds: 3),
          ),
        );

    final normalizedBase = _baseUrl.endsWith('/')
        ? _baseUrl.substring(0, _baseUrl.length - 1)
        : _baseUrl;
    final healthUrl = '$normalizedBase/api/v1/health';

    final stopwatch = Stopwatch()..start();
    try {
      final response = await client.get<dynamic>(
        healthUrl,
        options: Options(
          headers: <String, dynamic>{}, // explicit unauthenticated probe
          responseType: ResponseType.json,
        ),
      );
      stopwatch.stop();

      if (response.statusCode == 200) {
        return (
          reachable: true,
          rttMs: stopwatch.elapsedMilliseconds,
          error: null,
        );
      } else {
        return (
          reachable: false,
          rttMs: null,
          error: 'HTTP ${response.statusCode}',
        );
      }
    } catch (e) {
      stopwatch.stop();
      return (
        reachable: false,
        rttMs: null,
        error: e.toString(),
      );
    }
  }

  void _startPeriodicTimer() {
    _periodicTimer?.cancel();
    _periodicTimer = Timer.periodic(_periodicInterval, (_) {
      if (_isDisposed) return;
      if (_state.networkStatus == NetworkStatus.online) {
        probeBackend();
      } else if (_state.networkStatus == NetworkStatus.offline &&
          _lossStartedAt != null) {
        final now = _now();
        final lossDuration = now.difference(_lossStartedAt!);
        _updateState(_state.copyWith(
          connectivityLossDuration:
              lossDuration.isNegative ? Duration.zero : lossDuration,
          measuredAt: now,
        ));
      }
    });
  }

  void _updateState(ConnectivityState newState) {
    if (_isDisposed) return;
    _state = newState;
    if (!_stateController.isClosed) {
      _stateController.add(_state);
    }
  }

  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _connectivitySubscription?.cancel();
    _periodicTimer?.cancel();
    _stateController.close();
  }
}
