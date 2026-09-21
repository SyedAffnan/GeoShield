import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/geofencing/presentation/geofence_status_card.dart';
import '../../../core/location/device_location_service.dart';
import '../../../core/network/auth_exception.dart';
import '../../../core/network/connectivity_state.dart';
import '../../../core/network/network_exception.dart';
import '../../sos/data/sos_repository.dart';
import '../data/risk_repository.dart';
import 'sachet_disaster_alert_card.dart';
import '../../news/presentation/recent_safety_events_card.dart';

class RiskDashboardScreen extends ConsumerStatefulWidget {
  const RiskDashboardScreen({super.key});

  @override
  ConsumerState<RiskDashboardScreen> createState() =>
      _RiskDashboardScreenState();
}

class _RiskDashboardScreenState extends ConsumerState<RiskDashboardScreen> {
  bool _sosSending = false;
  String? _sosError;

  /// Shows confirmation dialog, obtains GPS location, and creates the SOS.
  ///
  /// Rules enforced:
  ///   1. Requires a valid GPS fix — never sends 0,0 or fake coordinates.
  ///   2. Shows a confirmation dialog before any network request is made.
  ///   3. Navigates to SosStatusScreen on success so the tourist tracks progress.
  ///   4. Shows a clear inline error on failure.
  Future<void> _triggerSos() async {
    // Step 1 — confirmation dialog
    final confirmed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        icon: Icon(Icons.sos_rounded,
            size: 40, color: Theme.of(context).colorScheme.error),
        title: const Text('Send Emergency SOS?'),
        content: const Text(
          'This will send your current GPS location to GeoShield emergency '
          'responders immediately.\n\n'
          'Only use this in a genuine emergency.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error),
            child: const Text('Send SOS'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _sosSending = true;
      _sosError = null;
    });

    try {
      // Step 2 — obtain real GPS fix
      final locationResult =
          await ref.read(deviceLocationServiceProvider).currentPosition();
      if (!mounted) return;
      if (locationResult is DeviceLocationFailure) {
        setState(() {
          _sosSending = false;
          _sosError = switch (locationResult.reason) {
            DeviceLocationFailureReason.servicesDisabled =>
              'Location services are disabled. Enable GPS and try again.',
            DeviceLocationFailureReason.permissionDenied ||
            DeviceLocationFailureReason.permissionDeniedForever =>
              'Location permission is required to send an SOS.',
            _ =>
              'Could not determine your current location. Move to an open area and try again.',
          };
        });
        return;
      }
      final fix = locationResult as DeviceLocationFix;

      // Step 3 — create SOS using the existing backend API
      final alert = await ref.read(sosRepositoryProvider).createSos(
            latitude: fix.latitude,
            longitude: fix.longitude,
            clientRequestId: generateClientRequestId(),
          );
      if (!mounted) return;

      setState(() => _sosSending = false);
      // Navigate to the SOS status screen with the alert as GoRouter extra
      if (mounted) context.push('/sos', extra: alert);
    } on AuthException {
      if (!mounted) return;
      setState(() {
        _sosSending = false;
        _sosError = 'Session expired. Please sign in again.';
      });
    } on NetworkException {
      if (!mounted) return;
      setState(() {
        _sosSending = false;
        _sosError = 'Unable to reach GeoShield. Check your connection and try again.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _sosSending = false;
        _sosError = 'SOS failed: ${e.toString()}';
      });
    }
  }

  @override
  Widget build(BuildContext context, ) {
    final state = ref.watch(riskDashboardProvider);
    final colorScheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('GeoShield Safety'),
        actions: [
          IconButton(
            tooltip: 'Refresh risk',
            onPressed: () => ref.read(riskDashboardProvider.notifier).refresh(),
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            tooltip: 'API configuration',
            onPressed: () async {
              await context.push('/settings');
              await ref.read(riskDashboardProvider.notifier).refresh();
            },
            icon: const Icon(Icons.settings_outlined),
          ),
          IconButton(
            tooltip: 'Sign out',
            onPressed: () async {
              await ref.read(authControllerProvider.notifier).logout();
              if (context.mounted) context.go('/login');
            },
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      // Prominent red SOS FAB — always visible regardless of dashboard state
      floatingActionButton: _sosSending
          ? FloatingActionButton.extended(
              onPressed: null,
              backgroundColor: colorScheme.error,
              foregroundColor: colorScheme.onError,
              icon: const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: Colors.white),
              ),
              label: const Text('Sending SOS…'),
            )
          : FloatingActionButton.extended(
              onPressed: _triggerSos,
              backgroundColor: colorScheme.error,
              foregroundColor: colorScheme.onError,
              icon: const Icon(Icons.sos_rounded),
              label: const Text(
                'SOS',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              tooltip: 'Send emergency SOS to responders',
            ),
      body: Column(
        children: [
          const _ConnectivityStatusBanner(),
          if (_sosError != null)
            Material(
              color: colorScheme.errorContainer,
              child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Row(
                  children: [
                    Icon(Icons.warning_amber_rounded,
                        color: colorScheme.onErrorContainer),
                    const SizedBox(width: 8),
                    Expanded(
                        child: Text(_sosError!,
                            style: TextStyle(
                                color: colorScheme.onErrorContainer))),
                    IconButton(
                      icon: Icon(Icons.close,
                          color: colorScheme.onErrorContainer),
                      onPressed: () => setState(() => _sosError = null),
                    ),
                  ],
                ),
              ),
            ),
          Expanded(
            child: switch (state) {
              RiskDashboardBusy(step: final step) => _Busy(step: step),
              RiskDashboardLocationBlocked(reason: final reason) =>
                _LocationBlocked(reason: reason),
              RiskDashboardFailed(kind: final kind, message: final message) =>
                _RequestFailed(kind: kind, message: message),
              RiskDashboardReady(data: final data) =>
                _DashboardContent(data: data),
            },
          ),
        ],
      ),
    );
  }
}

class _Busy extends StatelessWidget {
  const _Busy({required this.step});
  final RiskDashboardStep step;

  @override
  Widget build(BuildContext context) => Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const CircularProgressIndicator(),
          const SizedBox(height: 16),
          Text(switch (step) {
            RiskDashboardStep.obtainingLocation =>
              'Obtaining your current GPS location…',
            RiskDashboardStep.sendingLocation =>
              'Sending your location to GeoShield…',
            RiskDashboardStep.loadingRisk =>
              'Loading your safety score…',
          }),
        ]),
      );
}

/// No GPS fix was produced, so no risk request was made. Each reason offers the
/// one action that can actually resolve it.
class _LocationBlocked extends ConsumerWidget {
  const _LocationBlocked({required this.reason});
  final DeviceLocationFailureReason reason;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final (String message, String actionLabel) = switch (reason) {
      DeviceLocationFailureReason.servicesDisabled => (
          'Location services are switched off on this phone. Turn on location, then try again.',
          'Open location settings',
        ),
      DeviceLocationFailureReason.permissionDenied => (
          'GeoShield needs location permission to read your current position. '
              'Your safety score is calculated by the server from that position.',
          'Allow location access',
        ),
      DeviceLocationFailureReason.permissionDeniedForever => (
          'Location permission is permanently denied for GeoShield. Enable it in Android app '
              'settings under Permissions → Location, then return here.',
          'Open app settings',
        ),
      DeviceLocationFailureReason.timeout => (
          'Your phone did not return a GPS fix in time. Move to a spot with a clearer view of '
              'the sky and try again.',
          'Try again',
        ),
      DeviceLocationFailureReason.unavailable => (
          'Your current location is unavailable, so no safety score can be requested.',
          'Try again',
        ),
    };

    return _Message(
      icon: Icons.location_off,
      message: message,
      actionLabel: actionLabel,
      onPressed: () async {
        final service = ref.read(deviceLocationServiceProvider);
        switch (reason) {
          case DeviceLocationFailureReason.servicesDisabled:
            await service.openLocationSettings();
          case DeviceLocationFailureReason.permissionDeniedForever:
            await service.openAppSettings();
          case DeviceLocationFailureReason.permissionDenied:
          case DeviceLocationFailureReason.timeout:
          case DeviceLocationFailureReason.unavailable:
            break;
        }
        await ref.read(riskDashboardProvider.notifier).refresh();
      },
    );
  }
}

class _RequestFailed extends ConsumerWidget {
  const _RequestFailed({required this.kind, required this.message});
  final RiskDashboardFailureKind kind;
  final String? message;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isSessionFailure = kind == RiskDashboardFailureKind.session;
    return _Message(
      icon: isSessionFailure ? Icons.lock_outline : Icons.cloud_off,
      message: switch (kind) {
        RiskDashboardFailureKind.session =>
          'Your session has expired. Please sign in again.',
        RiskDashboardFailureKind.serverRejected => message ??
            'The server rejected the submitted location. Please try again.',
        RiskDashboardFailureKind.network =>
          'Unable to reach GeoShield. Check your connection or the configured backend URL, '
              'then try again.',
      },
      actionLabel: isSessionFailure ? 'Sign in' : 'Try again',
      onPressed: () async {
        if (isSessionFailure) {
          await ref.read(authControllerProvider.notifier).logout();
          if (context.mounted) context.go('/login');
          return;
        }
        await ref.read(riskDashboardProvider.notifier).refresh();
      },
    );
  }
}

class _Message extends StatelessWidget {
  const _Message({
    required this.icon,
    required this.message,
    required this.actionLabel,
    required this.onPressed,
  });
  final IconData icon;
  final String message;
  final String actionLabel;
  final Future<void> Function() onPressed;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 48),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(onPressed: onPressed, child: Text(actionLabel)),
          ]),
        ),
      );
}

class _DashboardContent extends ConsumerWidget {
  const _DashboardContent({required this.data});
  final RiskDashboardData data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final risk = data.risk;
    final color = _riskColor(context, risk.riskLevel);
    return RefreshIndicator(
      onRefresh: () => ref.read(riskDashboardProvider.notifier).refresh(),
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _LocationStatus(data: data),
          const SizedBox(height: 16),
          if (risk.dataCompleteness != null && risk.dataCompleteness!.degraded) ...[
            _DataCompletenessNoticeCard(completeness: risk.dataCompleteness!),
            const SizedBox(height: 16),
          ],
          if (risk.activeDisasterAlert != null) ...[
            SachetDisasterAlertCard(
              alert: risk.activeDisasterAlert!,
              overrideActive: risk.overrideActive,
            ),
            const SizedBox(height: 16),
          ],
          const RecentSafetyEventsCard(),
          const SizedBox(height: 16),
          const GeofenceStatusCard(),
          const SizedBox(height: 16),
          Card(
            color: (risk.overrideActive
                    ? Theme.of(context).colorScheme.error
                    : color)
                .withValues(alpha: 0.10),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(children: [
                Text(risk.overrideActive
                    ? 'CURRENT SAFETY SCORE (BASELINE PRESERVED)'
                    : 'CURRENT GEOSHIELD SAFETY SCORE'),
                const SizedBox(height: 12),
                Text(risk.safetyScore.toStringAsFixed(0),
                    style: Theme.of(context)
                        .textTheme
                        .displayLarge
                        ?.copyWith(color: color)),
                if (risk.overrideActive) ...[
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        'Baseline: ${risk.riskLevel}',
                        style: TextStyle(
                          color: Colors.grey.shade700,
                          decoration: TextDecoration.lineThrough,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(Icons.arrow_forward,
                          size: 16, color: Theme.of(context).colorScheme.error),
                      const SizedBox(width: 8),
                      Text(
                        'OVERRIDE: ${risk.effectiveRiskLevel ?? 'CRITICAL'}',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                    ],
                  ),
                ] else ...[
                  Text(risk.riskLevel,
                      style: Theme.of(context)
                          .textTheme
                          .titleLarge
                          ?.copyWith(color: color, fontWeight: FontWeight.bold)),
                ],
                const SizedBox(height: 16),
                Text(risk.recommendation, textAlign: TextAlign.center),
                if (risk.decisionId != null) ...[
                  const SizedBox(height: 12),
                  Text(
                    'Audit Decision ID: ${risk.decisionId}',
                    style: TextStyle(
                      fontSize: 11,
                      fontFamily: 'monospace',
                      color: Colors.grey.shade600,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
              ]),
            ),
          ),
          const SizedBox(height: 24),
          Text('Risk factors', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          ...risk.factors.map((factor) {
            final detail = risk.factorDetails.where((d) => d.factor == factor.factor).firstOrNull;
            return RiskFactorCard(factor: factor, detail: detail);
          }),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => context.push('/incidents'),
            icon: const Icon(Icons.report_outlined),
            label: const Text('View or report incidents'),
          ),
          const SizedBox(height: 8),
          const Text(
            'Safety scores and factor availability are determined by the GeoShield backend. Experimental AI/ML is not used in this score.',
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Color _riskColor(BuildContext context, String level) => switch (level) {
        'LOW' => Colors.green,
        'MEDIUM' => Colors.amber.shade800,
        'HIGH' => Colors.orange,
        'CRITICAL' => Theme.of(context).colorScheme.error,
        _ => Theme.of(context).colorScheme.primary,
      };
}

class _LocationStatus extends StatelessWidget {
  const _LocationStatus({required this.data});
  final RiskDashboardData data;

  @override
  Widget build(BuildContext context) {
    final fix = data.fix;
    final accuracy = fix.accuracy;
    return Card(
      child: ListTile(
        leading: const Icon(Icons.my_location),
        title: const Text('Live GPS location sent to GeoShield'),
        subtitle: Text([
          '${data.storedLocation.latitude.toStringAsFixed(5)}, '
              '${data.storedLocation.longitude.toStringAsFixed(5)}',
          if (accuracy != null) 'Accuracy ${accuracy.toStringAsFixed(0)} m',
          'Fix taken ${fix.timestamp.toLocal().toIso8601String().substring(11, 19)}',
        ].join(' · ')),
      ),
    );
  }
}

class RiskFactorCard extends StatelessWidget {
  const RiskFactorCard({super.key, required this.factor, this.detail});
  final RiskFactor factor;
  final RiskFactorDetailModel? detail;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(_labelFor(factor.factor),
                    style: Theme.of(context).textTheme.titleMedium),
                if (detail != null)
                  Text(
                    'Weight ${(detail!.weight * 100).toStringAsFixed(0)}%',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey.shade600,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 4),
            Text(factor.available ? 'AVAILABLE' : 'UNAVAILABLE',
                style: TextStyle(
                    color: factor.available ? Colors.green : Colors.grey,
                    fontWeight: FontWeight.w600)),
            if (factor.available) ...[
              const SizedBox(height: 8),
              if (detail?.rawValue != null && detail!.rawValue!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(
                    'Observed: ${detail!.rawValue}',
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade800),
                  ),
                ),
              Text('Risk: ${(factor.normalizedRisk ?? 0).toStringAsFixed(2)}'),
              Text('Contribution: ${factor.contribution.toStringAsFixed(2)} pts'),
            ] else if (detail?.reason != null) ...[
              const SizedBox(height: 8),
              Text(
                'Reason: ${detail!.reason}',
                style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey.shade700,
                    fontStyle: FontStyle.italic),
              ),
            ],
            if (detail?.source != null && detail!.source!.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(
                'Source: ${detail!.source}',
                style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
              ),
            ],
            const SizedBox(height: 8),
            Text(factor.explanation),
          ]),
        ),
      );

  String _labelFor(String factorName) => switch (factorName) {
        'HISTORICAL_INCIDENT' => 'Historical Risk',
        'TIME_OF_DAY' => 'Time of Day',
        'SERVICE_PROXIMITY' => 'Service Proximity',
        'USER_REPORT' => 'User Report',
        'OTHER_CONTEXT' => 'Other Context',
        'CONNECTIVITY' => 'Connectivity',
        'WEATHER' => 'Weather',
        _ => factorName,
      };
}

class _ConnectivityStatusBanner extends ConsumerWidget {
  const _ConnectivityStatusBanner();

  String _formatDuration(Duration d) {
    if (d.inHours > 0) {
      return '${d.inHours}h ${d.inMinutes.remainder(60)}m';
    } else if (d.inMinutes > 0) {
      return '${d.inMinutes}m ${d.inSeconds.remainder(60)}s';
    } else {
      return '${d.inSeconds}s';
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final connectivityAsync = ref.watch(connectivityStateProvider);
    final state = connectivityAsync.asData?.value;
    if (state == null) {
      return const SizedBox.shrink();
    }

    final (IconData icon, Color bgColor, Color fgColor, String text) =
        switch (state.status) {
      OperationalConnectivityStatus.connectedReachable => (
          Icons.cloud_done_outlined,
          Colors.green.shade50,
          Colors.green.shade800,
          'Online • Backend reachable${state.backendRttMs != null ? ' (${state.backendRttMs} ms)' : ''}',
        ),
      OperationalConnectivityStatus.connectedBackendUnreachable => (
          Icons.cloud_off_outlined,
          Colors.amber.shade50,
          Colors.amber.shade900,
          'Connected • Backend unreachable${state.connectivityLossDuration.inSeconds > 0 ? ' (${_formatDuration(state.connectivityLossDuration)})' : ''}',
        ),
      OperationalConnectivityStatus.offline => (
          Icons.wifi_off_outlined,
          Colors.red.shade50,
          Colors.red.shade900,
          'Offline${state.connectivityLossDuration.inSeconds > 0 ? ' (${_formatDuration(state.connectivityLossDuration)})' : ''}',
        ),
      OperationalConnectivityStatus.unknown => (
          Icons.sync,
          Colors.grey.shade100,
          Colors.grey.shade700,
          'Checking connectivity…',
        ),
    };

    return Container(
      width: double.infinity,
      color: bgColor,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Row(
        children: [
          Icon(icon, size: 16, color: fgColor),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w500,
                color: fgColor,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DataCompletenessNoticeCard extends StatelessWidget {
  const _DataCompletenessNoticeCard({required this.completeness});
  final RiskDataCompletenessModel completeness;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Colors.amber.shade700.withValues(alpha: 0.4)),
      ),
      color: Colors.amber.shade50.withValues(alpha: 0.5),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.info_outline, size: 20, color: Colors.amber.shade800),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Informational: Partial Data Availability (${completeness.availableFactorCount}/${completeness.expectedLiveFactorCount} factors)',
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 13,
                      color: Colors.amber.shade900,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Baseline score reflects only verified live data signals. No artificial penalties are applied for unavailable factors.',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey.shade800,
                    ),
                  ),
                  if (completeness.missingReasons.isNotEmpty) ...[
                    const SizedBox(height: 6),
                    ...completeness.missingReasons.entries.map((entry) => Padding(
                          padding: const EdgeInsets.only(top: 2),
                          child: Text(
                            '• ${_humanizeFactorName(entry.key)}: ${_humanizeReasonCode(entry.value)}',
                            style: TextStyle(
                              fontSize: 11,
                              color: Colors.grey.shade800,
                            ),
                          ),
                        )),
                  ] else if (completeness.missingFactors.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      'Unavailable: ${completeness.missingFactors.map(_humanizeFactorName).join(", ")}',
                      style: TextStyle(
                        fontSize: 11,
                        color: Colors.grey.shade700,
                        fontStyle: FontStyle.italic,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  static String _humanizeFactorName(String factorName) => switch (factorName) {
        'HISTORICAL_INCIDENT' => 'Historical Risk',
        'TIME_OF_DAY' => 'Time of Day',
        'SERVICE_PROXIMITY' => 'Service Proximity',
        'USER_REPORT' => 'User Report',
        'OTHER_CONTEXT' => 'Other Context',
        'CONNECTIVITY' => 'Connectivity',
        'WEATHER' => 'Weather',
        _ => factorName,
      };

  static String _humanizeReasonCode(String code) => switch (code) {
        'NO_COORDINATES' => 'Location coordinates not provided',
        'STATE_RESOLUTION_FAILED' => 'State/UT boundary lookup unavailable',
        'MORTH_DATA_UNAVAILABLE' => 'Historical road safety baseline unavailable',
        'NCRB_DATA_UNAVAILABLE' => 'Historical crime statistics unavailable',
        'OPEN_METEO_DISABLED' => 'Weather provider disabled by configuration',
        'OPEN_METEO_UNREACHABLE' => 'Weather provider temporarily unreachable',
        'OPEN_METEO_ERROR' => 'Weather provider returned an error',
        'INCIDENT_MODULE_UNAVAILABLE' => 'Incident feed unavailable for current location',
        'EMERGENCY_SERVICES_UNAVAILABLE' => 'Emergency service proximity lookup not configured',
        _ => code.replaceAll('_', ' ').toLowerCase(),
      };
}

