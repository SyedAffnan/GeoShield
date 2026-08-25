import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/network/auth_exception.dart';
import '../data/risk_repository.dart';

class RiskDashboardScreen extends ConsumerWidget {
  const RiskDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashboard = ref.watch(riskDashboardProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('GeoShield Safety'),
        actions: [
          IconButton(
            tooltip: 'Refresh risk',
            onPressed: () => ref.invalidate(riskDashboardProvider),
            icon: const Icon(Icons.refresh),
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
      body: dashboard.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _DashboardError(error: error),
        data: (data) => _DashboardContent(data: data),
      ),
    );
  }
}

class _DashboardError extends ConsumerWidget {
  const _DashboardError({required this.error});
  final Object error;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isAuthFailure = error is AuthException;
    final isLocationUnavailable = error is LocationUnavailableException;
    final message = isAuthFailure
        ? 'Your session has expired. Please sign in again.'
        : isLocationUnavailable
            ? 'Current location is unavailable. Update your location before requesting safety information.'
            : 'Unable to load current safety information. Please check your connection and try again.';
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(isLocationUnavailable ? Icons.location_off : Icons.cloud_off,
              size: 48),
          const SizedBox(height: 16),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () async {
              if (isAuthFailure) {
                await ref.read(authControllerProvider.notifier).logout();
                if (context.mounted) context.go('/login');
              } else {
                ref.invalidate(riskDashboardProvider);
              }
            },
            child: Text(isAuthFailure ? 'Sign in' : 'Refresh risk'),
          ),
        ]),
      ),
    );
  }
}

class _DashboardContent extends StatelessWidget {
  const _DashboardContent({required this.data});
  final RiskDashboardData data;

  @override
  Widget build(BuildContext context) {
    final risk = data.risk;
    final color = _riskColor(context, risk.riskLevel);
    return RefreshIndicator(
      onRefresh: () async {},
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _LocationStatus(location: data.location),
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
  const _LocationStatus({required this.location});
  final CurrentLocation location;

  @override
  Widget build(BuildContext context) => Card(
        child: ListTile(
          leading: const Icon(Icons.location_on_outlined),
          title: const Text('Current location available'),
          subtitle: Text(
              'Coordinates on file: ${location.latitude.toStringAsFixed(4)}, ${location.longitude.toStringAsFixed(4)}'),
        ),
      );
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
