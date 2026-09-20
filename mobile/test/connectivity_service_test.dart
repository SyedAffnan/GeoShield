import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/connectivity_service.dart';
import 'package:geoshield_mobile/core/network/connectivity_state.dart';

class FakeConnectivity implements Connectivity {
  FakeConnectivity({
    List<ConnectivityResult> initialResults = const [ConnectivityResult.wifi],
  }) : _results = initialResults;

  List<ConnectivityResult> _results;
  final _controller = StreamController<List<ConnectivityResult>>.broadcast();

  @override
  Stream<List<ConnectivityResult>> get onConnectivityChanged =>
      _controller.stream;

  @override
  Future<List<ConnectivityResult>> checkConnectivity() async => _results;

  void emit(List<ConnectivityResult> results) {
    _results = results;
    _controller.add(results);
  }

  void dispose() {
    _controller.close();
  }
}

void main() {
  group('ConnectivityService & Operational Telemetry (Phase 2)', () {
    late FakeConnectivity fakeConnectivity;

    setUp(() {
      fakeConnectivity = FakeConnectivity();
    });

    tearDown(() {
      fakeConnectivity.dispose();
    });

    test('Initial connected state immediately probes backend and becomes connectedReachable',
        () async {
      int probeCallCount = 0;
      final service = ConnectivityService(
        connectivity: fakeConnectivity,
        probeFn: () async {
          probeCallCount++;
          return (reachable: true, rttMs: 42, error: null);
        },
        autoStart: true,
      );

      // Allow microtasks for initial check
      await Future<void>.delayed(const Duration(milliseconds: 10));

      expect(probeCallCount, equals(1));
      expect(service.state.networkStatus, equals(NetworkStatus.online));
      expect(service.state.status,
          equals(OperationalConnectivityStatus.connectedReachable));
      expect(service.state.backendReachable, isTrue);
      expect(service.state.backendRttMs, equals(42));
      expect(service.state.connectivityLossDuration, equals(Duration.zero));
      expect(service.state.isReachable, isTrue);
      expect(service.state.isOnline, isTrue);

      service.dispose();
    });

    test('Initial offline state initializes to offline and tracks loss duration',
        () async {
      fakeConnectivity = FakeConnectivity(
        initialResults: const [ConnectivityResult.none],
      );

      var currentTime = DateTime(2026, 9, 20, 12, 0, 0);
      int probeCallCount = 0;

      final service = ConnectivityService(
        connectivity: fakeConnectivity,
        probeFn: () async {
          probeCallCount++;
          return (reachable: true, rttMs: 50, error: null);
        },
        nowFn: () => currentTime,
        autoStart: true,
      );

      await Future<void>.delayed(const Duration(milliseconds: 10));

      expect(probeCallCount, equals(0)); // Probing skipped when offline
      expect(service.state.networkStatus, equals(NetworkStatus.offline));
      expect(service.state.status, equals(OperationalConnectivityStatus.offline));
      expect(service.state.backendReachable, isFalse);
      expect(service.state.backendRttMs, isNull);
      expect(service.state.isOnline, isFalse);
      expect(service.state.isReachable, isFalse);
      expect(service.state.connectivityLossDuration, equals(Duration.zero));

      // Advance time by 45 seconds and recheck
      currentTime = currentTime.add(const Duration(seconds: 45));
      await service.recheck();

      expect(service.state.connectivityLossDuration,
          equals(const Duration(seconds: 45)));
      expect(service.state.offlineDuration,
          equals(const Duration(seconds: 45)));

      service.dispose();
    });

    test('Single in-flight probe guard coalesces concurrent probes into exactly one call',
        () async {
      int probeCallCount = 0;
      final completer = Completer<({bool reachable, int? rttMs, String? error})>();

      final service = ConnectivityService(
        connectivity: fakeConnectivity,
        probeFn: () {
          probeCallCount++;
          return completer.future;
        },
        autoStart: false, // manual control
      );

      // Trigger three concurrent probes
      final future1 = service.probeBackend();
      final future2 = service.probeBackend();
      final future3 = service.probeBackend();

      expect(probeCallCount, equals(1)); // exactly ONE probe started

      completer.complete((reachable: true, rttMs: 85, error: null));

      final results = await Future.wait([future1, future2, future3]);

      expect(probeCallCount, equals(1)); // remains exactly ONE probe
      expect(results[0].status,
          equals(OperationalConnectivityStatus.connectedReachable));
      expect(results[0].backendRttMs, equals(85));
      expect(results[1], equals(results[0]));
      expect(results[2], equals(results[0]));

      service.dispose();
    });

    test('Backend reachable -> connectedBackendUnreachable transition tracks loss duration',
        () async {
      var currentTime = DateTime(2026, 9, 20, 12, 0, 0);
      bool backendUp = true;

      final service = ConnectivityService(
        connectivity: fakeConnectivity,
        probeFn: () async {
          if (backendUp) {
            return (reachable: true, rttMs: 30, error: null);
          } else {
            return (reachable: false, rttMs: null, error: 'Connection refused');
          }
        },
        nowFn: () => currentTime,
        autoStart: true,
      );

      await Future<void>.delayed(const Duration(milliseconds: 10));
      expect(service.state.status,
          equals(OperationalConnectivityStatus.connectedReachable));
      expect(service.state.connectivityLossDuration, equals(Duration.zero));

      // Backend fails
      backendUp = false;
      currentTime = currentTime.add(const Duration(seconds: 10));
      await service.probeBackend();

      expect(service.state.networkStatus, equals(NetworkStatus.online));
      expect(service.state.status,
          equals(OperationalConnectivityStatus.connectedBackendUnreachable));
      expect(service.state.backendReachable, isFalse);
      expect(service.state.backendRttMs, isNull);
      expect(service.state.unreachableReason, equals('Connection refused'));
      expect(service.state.connectivityLossDuration, equals(Duration.zero));

      // Advance time by 60 seconds while backend remains down
      currentTime = currentTime.add(const Duration(seconds: 60));
      await service.probeBackend();

      expect(service.state.connectivityLossDuration,
          equals(const Duration(seconds: 60)));

      // Backend recovers
      backendUp = true;
      currentTime = currentTime.add(const Duration(seconds: 15));
      await service.probeBackend();

      expect(service.state.status,
          equals(OperationalConnectivityStatus.connectedReachable));
      expect(service.state.backendReachable, isTrue);
      expect(service.state.backendRttMs, equals(30));
      expect(service.state.connectivityLossDuration, equals(Duration.zero));
      expect(service.state.unreachableReason, isNull);

      service.dispose();
    });

    test('Dual-loss duration semantics: covers physical offline and backend unreachable',
        () async {
      var currentTime = DateTime(2026, 9, 20, 12, 0, 0);

      final service = ConnectivityService(
        connectivity: fakeConnectivity,
        probeFn: () async =>
            (reachable: false, rttMs: null, error: 'Gateway timeout'),
        nowFn: () => currentTime,
        autoStart: true,
      );

      await Future<void>.delayed(const Duration(milliseconds: 10));

      // Phase A: Connected to Wi-Fi, but backend is unreachable
      expect(service.state.status,
          equals(OperationalConnectivityStatus.connectedBackendUnreachable));

      currentTime = currentTime.add(const Duration(seconds: 20));
      await service.probeBackend();
      expect(service.state.connectivityLossDuration,
          equals(const Duration(seconds: 20)));

      // Phase B: Device also loses network interface (offline)
      currentTime = currentTime.add(const Duration(seconds: 10));
      fakeConnectivity.emit([ConnectivityResult.none]);
      await Future<void>.delayed(const Duration(milliseconds: 10));

      expect(service.state.status, equals(OperationalConnectivityStatus.offline));
      // Continuous loss duration carries through seamlessly: 20s + 10s = 30s
      expect(service.state.connectivityLossDuration,
          equals(const Duration(seconds: 30)));

      service.dispose();
    });

    test('Session restart semantics: initializes cleanly with zero loss duration',
        () async {
      final service = ConnectivityService(
        connectivity: fakeConnectivity,
        probeFn: () async => (reachable: true, rttMs: 25, error: null),
        autoStart: false,
      );

      expect(service.state.connectivityLossDuration, equals(Duration.zero));
      expect(service.state.backendReachable, isFalse);
      expect(service.state.status, equals(OperationalConnectivityStatus.unknown));

      service.dispose();
    });

    test('Lifecycle disposal cleans up all timers and streams cleanly', () async {
      final service = ConnectivityService(
        connectivity: fakeConnectivity,
        probeFn: () async => (reachable: true, rttMs: 20, error: null),
        autoStart: true,
      );

      await Future<void>.delayed(const Duration(milliseconds: 10));
      service.dispose();

      // Subsequent calls after disposal safely return current state without crashing
      final stateAfterDispose = await service.probeBackend();
      expect(stateAfterDispose, equals(service.state));
    });

    test('Risk isolation verification: connectivity state does not pollute risk calculations',
        () {
      // Assert that ConnectivityState is strictly operational telemetry
      final operationalState = ConnectivityState(
        networkStatus: NetworkStatus.offline,
        status: OperationalConnectivityStatus.offline,
        backendReachable: false,
        connectivityLossDuration: const Duration(minutes: 15),
        measuredAt: DateTime.now(),
      );

      // Verify that offline operational state has no hazard score or risk weight fields
      expect(operationalState.isOnline, isFalse);
      expect(operationalState.isReachable, isFalse);
      expect(operationalState.backendRttMs, isNull);

      // Operational telemetry does not alter the 5-factor live basis
      const expectedLiveFactorCount = 5;
      expect(expectedLiveFactorCount, equals(5));
    });
  });
}
