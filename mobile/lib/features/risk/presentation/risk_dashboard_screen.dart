import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/location/device_location_service.dart';
import '../data/risk_repository.dart';

class RiskDashboardScreen extends ConsumerWidget {
  const RiskDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(riskDashboardProvider);
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
      body: switch (state) {
        RiskDashboardBusy(step: final step) => _Busy(step: step),
        RiskDashboardLocationBlocked(reason: final reason) =>
          _LocationBlocked(reason: reason),
        RiskDashboardFailed(kind: final kind, message: final message) =>
          _RequestFailed(kind: kind, message: message),
        RiskDashboardReady(data: final data) => _DashboardContent(data: data),
      },
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
          Card(
            color: color.withValues(alpha: 0.10),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(children: [
                const Text('CURRENT GEOSHIELD SAFETY SCORE'),
                const SizedBox(height: 12),
                Text(risk.safetyScore.toStringAsFixed(0),
                    style: Theme.of(context)
                        .textTheme
                        .displayLarge
                        ?.copyWith(color: color)),
                Text(risk.riskLevel,
                    style: Theme.of(context)
                        .textTheme
                        .titleLarge
                        ?.copyWith(color: color, fontWeight: FontWeight.bold)),
                const SizedBox(height: 16),
                Text(risk.recommendation, textAlign: TextAlign.center),
              ]),
            ),
          ),
          const SizedBox(height: 24),
          Text('Risk factors', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          ...risk.factors.map((factor) => _RiskFactorCard(factor: factor)),
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

class _RiskFactorCard extends StatelessWidget {
  const _RiskFactorCard({required this.factor});
  final RiskFactor factor;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(_labelFor(factor.factor),
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(factor.available ? 'AVAILABLE' : 'UNAVAILABLE',
                style: TextStyle(
                    color: factor.available ? Colors.green : Colors.grey)),
            if (factor.available) ...[
              const SizedBox(height: 8),
              Text('Risk: ${factor.normalizedRisk!.toStringAsFixed(2)}'),
              Text('Contribution: ${factor.contribution.toStringAsFixed(2)}'),
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
