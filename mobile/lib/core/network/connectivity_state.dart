enum NetworkStatus {
  online,
  offline,
  unknown,
}

enum OperationalConnectivityStatus {
  unknown,
  offline,
  connectedBackendUnreachable,
  connectedReachable,
}

/// Runtime operational connectivity telemetry.
/// Strictly operational state indicator; NEVER a physical risk factor.
class ConnectivityState {
  const ConnectivityState({
    required this.networkStatus,
    required this.status,
    required this.backendReachable,
    required this.connectivityLossDuration,
    required this.measuredAt,
    this.backendRttMs,
    this.unreachableReason,
  });

  final NetworkStatus networkStatus;
  final OperationalConnectivityStatus status;
  final bool backendReachable;
  final int? backendRttMs;
  final Duration connectivityLossDuration;
  final DateTime measuredAt;
  final String? unreachableReason;

  /// Backward-compatible alias for continuous operational loss duration.
  Duration get offlineDuration => connectivityLossDuration;
  bool get isOnline => networkStatus == NetworkStatus.online;
  bool get isReachable => status == OperationalConnectivityStatus.connectedReachable;

  factory ConnectivityState.initial() => ConnectivityState(
        networkStatus: NetworkStatus.unknown,
        status: OperationalConnectivityStatus.unknown,
        backendReachable: false,
        connectivityLossDuration: Duration.zero,
        measuredAt: DateTime.now(),
      );

  ConnectivityState copyWith({
    NetworkStatus? networkStatus,
    OperationalConnectivityStatus? status,
    bool? backendReachable,
    int? backendRttMs,
    Duration? connectivityLossDuration,
    DateTime? measuredAt,
    String? unreachableReason,
    bool clearRtt = false,
    bool clearReason = false,
  }) {
    return ConnectivityState(
      networkStatus: networkStatus ?? this.networkStatus,
      status: status ?? this.status,
      backendReachable: backendReachable ?? this.backendReachable,
      backendRttMs: clearRtt ? null : (backendRttMs ?? this.backendRttMs),
      connectivityLossDuration:
          connectivityLossDuration ?? this.connectivityLossDuration,
      measuredAt: measuredAt ?? this.measuredAt,
      unreachableReason:
          clearReason ? null : (unreachableReason ?? this.unreachableReason),
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ConnectivityState &&
          runtimeType == other.runtimeType &&
          networkStatus == other.networkStatus &&
          status == other.status &&
          backendReachable == other.backendReachable &&
          backendRttMs == other.backendRttMs &&
          connectivityLossDuration == other.connectivityLossDuration &&
          unreachableReason == other.unreachableReason;

  @override
  int get hashCode => Object.hash(
        networkStatus,
        status,
        backendReachable,
        backendRttMs,
        connectivityLossDuration,
        unreachableReason,
      );

  @override
  String toString() =>
      'ConnectivityState(network: $networkStatus, status: $status, reachable: $backendReachable, rtt: ${backendRttMs}ms, lossDuration: ${connectivityLossDuration.inSeconds}s, reason: $unreachableReason)';
}
