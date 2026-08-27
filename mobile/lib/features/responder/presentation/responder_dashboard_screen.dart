import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../data/responder_repository.dart';

final responderIncidentsProvider =
    FutureProvider.autoDispose<List<ResponderIncident>>((ref) async {
  return ref.watch(responderRepositoryProvider).fetchIncidentQueue();
});

final responderSosProvider =
    FutureProvider.autoDispose<List<SosItem>>((ref) async {
  return ref.watch(responderRepositoryProvider).fetchSosQueue();
});

class ResponderDashboardScreen extends ConsumerWidget {
  const ResponderDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Emergency Response Console'),
          bottom: const TabBar(
            tabs: [
              Tab(icon: Icon(Icons.warning_amber), text: 'Incident Queue'),
              Tab(icon: Icon(Icons.sos), text: 'Emergency SOS'),
            ],
          ),
          actions: [
            IconButton(
              tooltip: 'Refresh',
              onPressed: () {
                ref.invalidate(responderIncidentsProvider);
                ref.invalidate(responderSosProvider);
              },
              icon: const Icon(Icons.refresh),
            ),
            IconButton(
              tooltip: 'API configuration',
              onPressed: () => context.push('/settings'),
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
        body: const TabBarView(
          children: [
            _IncidentsQueueTab(),
            _SosQueueTab(),
          ],
        ),
      ),
    );
  }
}

class _IncidentsQueueTab extends ConsumerWidget {
  const _IncidentsQueueTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final incidentsAsync = ref.watch(responderIncidentsProvider);

    return incidentsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.red),
              const SizedBox(height: 16),
              Text('Failed to load incident queue:\n$error',
                  textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.invalidate(responderIncidentsProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
      data: (incidents) {
        if (incidents.isEmpty) {
          return Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.verified_outlined,
                    size: 64, color: Colors.green),
                const SizedBox(height: 16),
                Text(
                  'No active incidents in queue',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                const Text('All reported incidents have been resolved.'),
              ],
            ),
          );
        }

        return RefreshIndicator(
          onRefresh: () async => ref.invalidate(responderIncidentsProvider),
          child: ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: incidents.length,
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final incident = incidents[index];
              return _ResponderIncidentCard(
                incident: incident,
                onStatusUpdate: (nextStatus) async {
                  try {
                    await ref
                        .read(responderRepositoryProvider)
                        .updateIncidentStatus(
                          incident.incidentId,
                          nextStatus,
                        );
                    ref.invalidate(responderIncidentsProvider);
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                              'Incident status updated to $nextStatus'),
                          backgroundColor: Colors.green,
                        ),
                      );
                    }
                  } catch (e) {
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Status transition error: $e'),
                          backgroundColor: Colors.red,
                        ),
                      );
                    }
                  }
                },
              );
            },
          ),
        );
      },
    );
  }
}

class _ResponderIncidentCard extends StatelessWidget {
  const _ResponderIncidentCard({
    required this.incident,
    required this.onStatusUpdate,
  });

  final ResponderIncident incident;
  final ValueChanged<String> onStatusUpdate;

  Color _statusColor(String status) {
    switch (status.toUpperCase()) {
      case 'RESOLVED':
        return Colors.green;
      case 'RESPONDING':
        return Colors.orange;
      case 'ACKNOWLEDGED':
        return Colors.blue;
      case 'REPORTED':
      default:
        return Colors.red;
    }
  }

  @override
  Widget build(BuildContext context) {
    final statusColor = _statusColor(incident.status);

    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(Icons.report_problem, color: statusColor),
                    const SizedBox(width: 8),
                    Text(
                      incident.incidentType,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: statusColor),
                  ),
                  child: Text(
                    incident.status,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: statusColor,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              incident.description,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Theme.of(context)
                    .colorScheme
                    .surfaceContainerHighest
                    .withValues(alpha: 0.5),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.person, size: 16),
                      const SizedBox(width: 6),
                      Text(
                        '${incident.reporterFullName} (@${incident.reporterUsername})',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.phone, size: 16),
                      const SizedBox(width: 6),
                      Text(incident.reporterPhoneNumber),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.pin_drop, size: 16),
                      const SizedBox(width: 6),
                      Text(
                        '${incident.latitude.toStringAsFixed(4)}, ${incident.longitude.toStringAsFixed(4)}',
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (incident.status.toUpperCase() == 'REPORTED')
                  FilledButton.icon(
                    onPressed: () => onStatusUpdate('ACKNOWLEDGED'),
                    icon: const Icon(Icons.check, size: 16),
                    label: const Text('Accept Incident'),
                  ),
                if (incident.status.toUpperCase() == 'ACKNOWLEDGED')
                  FilledButton.icon(
                    onPressed: () => onStatusUpdate('RESPONDING'),
                    icon: const Icon(Icons.directions_run, size: 16),
                    label: const Text('Mark En Route'),
                    style: FilledButton.styleFrom(backgroundColor: Colors.orange),
                  ),
                if (incident.status.toUpperCase() == 'RESPONDING')
                  FilledButton.icon(
                    onPressed: () => onStatusUpdate('RESOLVED'),
                    icon: const Icon(Icons.task_alt, size: 16),
                    label: const Text('Mark Resolved'),
                    style: FilledButton.styleFrom(backgroundColor: Colors.green),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _SosQueueTab extends ConsumerWidget {
  const _SosQueueTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sosAsync = ref.watch(responderSosProvider);

    return sosAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.red),
              const SizedBox(height: 16),
              Text('Failed to load SOS queue:\n$error',
                  textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.invalidate(responderSosProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
      data: (sosList) {
        if (sosList.isEmpty) {
          return Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.shield_outlined,
                    size: 64, color: Colors.green),
                const SizedBox(height: 16),
                Text(
                  'No active SOS alerts',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                const Text('All emergency requests are clear.'),
              ],
            ),
          );
        }

        return RefreshIndicator(
          onRefresh: () async => ref.invalidate(responderSosProvider),
          child: ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: sosList.length,
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final sos = sosList[index];
              return _SosCard(
                sos: sos,
                onStatusUpdate: (nextStatus) async {
                  try {
                    await ref
                        .read(responderRepositoryProvider)
                        .updateSosStatus(sos.sosId, nextStatus);
                    ref.invalidate(responderSosProvider);
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('SOS status updated to $nextStatus'),
                          backgroundColor: Colors.green,
                        ),
                      );
                    }
                  } catch (e) {
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('SOS transition error: $e'),
                          backgroundColor: Colors.red,
                        ),
                      );
                    }
                  }
                },
              );
            },
          ),
        );
      },
    );
  }
}

class _SosCard extends StatelessWidget {
  const _SosCard({
    required this.sos,
    required this.onStatusUpdate,
  });

  final SosItem sos;
  final ValueChanged<String> onStatusUpdate;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 3,
      shape: RoundedRectangleBorder(
        side: const BorderSide(color: Colors.red, width: 1.5),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: const [
                    Icon(Icons.warning, color: Colors.red, size: 24),
                    SizedBox(width: 8),
                    Text(
                      'EMERGENCY SOS ALERT',
                      style: TextStyle(
                        color: Colors.red,
                        fontWeight: FontWeight.bold,
                        fontSize: 15,
                      ),
                    ),
                  ],
                ),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.red.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.red),
                  ),
                  child: Text(
                    sos.status,
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: Colors.red,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Theme.of(context)
                    .colorScheme
                    .surfaceContainerHighest
                    .withValues(alpha: 0.5),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.person, size: 16),
                      const SizedBox(width: 6),
                      Text(
                        '${sos.fullName} (@${sos.username})',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.phone, size: 16),
                      const SizedBox(width: 6),
                      Text(sos.phoneNumber),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.pin_drop, size: 16),
                      const SizedBox(width: 6),
                      Text(
                        '${sos.latitude.toStringAsFixed(4)}, ${sos.longitude.toStringAsFixed(4)}',
                      ),
                    ],
                  ),
                  if (sos.triggeredAt != null) ...[
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        const Icon(Icons.access_time, size: 16),
                        const SizedBox(width: 6),
                        Text(
                          sos.triggeredAt!.toLocal().toString().split('.')[0],
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (sos.status.toUpperCase() == 'PENDING')
                  FilledButton.icon(
                    onPressed: () => onStatusUpdate('ACKNOWLEDGED'),
                    icon: const Icon(Icons.check, size: 16),
                    label: const Text('Accept SOS'),
                    style: FilledButton.styleFrom(backgroundColor: Colors.blue),
                  ),
                if (sos.status.toUpperCase() == 'ACKNOWLEDGED')
                  FilledButton.icon(
                    onPressed: () => onStatusUpdate('RESPONDING'),
                    icon: const Icon(Icons.directions_run, size: 16),
                    label: const Text('Mark En Route'),
                    style:
                        FilledButton.styleFrom(backgroundColor: Colors.orange),
                  ),
                if (sos.status.toUpperCase() == 'RESPONDING')
                  FilledButton.icon(
                    onPressed: () => onStatusUpdate('RESOLVED'),
                    icon: const Icon(Icons.task_alt, size: 16),
                    label: const Text('Mark Resolved'),
                    style:
                        FilledButton.styleFrom(backgroundColor: Colors.green),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
