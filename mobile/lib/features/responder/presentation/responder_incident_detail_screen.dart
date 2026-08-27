import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../data/responder_repository.dart';

final responderIncidentDetailProvider = FutureProvider.autoDispose
    .family<ResponderIncident, String>((ref, incidentId) async {
  return ref.watch(responderRepositoryProvider).fetchIncident(incidentId);
});

class ResponderIncidentDetailScreen extends ConsumerWidget {
  const ResponderIncidentDetailScreen({
    super.key,
    required this.incidentId,
  });

  final String incidentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final incidentAsync = ref.watch(responderIncidentDetailProvider(incidentId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Incident Response Detail'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: () =>
                ref.invalidate(responderIncidentDetailProvider(incidentId)),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: incidentAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, size: 48, color: Colors.red),
                const SizedBox(height: 16),
                Text('Failed to load incident detail:\n$error',
                    textAlign: TextAlign.center),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => ref
                      .invalidate(responderIncidentDetailProvider(incidentId)),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
        data: (incident) => SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            incident.incidentType,
                            style: const TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          Chip(label: Text(incident.status)),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Text(
                        incident.description,
                        style: Theme.of(context).textTheme.bodyLarge,
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Tourist Information',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 12),
                      ListTile(
                        leading: const Icon(Icons.person),
                        title: Text(incident.reporterFullName),
                        subtitle: Text('@${incident.reporterUsername}'),
                        contentPadding: EdgeInsets.zero,
                      ),
                      ListTile(
                        leading: const Icon(Icons.phone),
                        title: Text(incident.reporterPhoneNumber),
                        contentPadding: EdgeInsets.zero,
                      ),
                      ListTile(
                        leading: const Icon(Icons.location_on),
                        title: const Text('Incident Coordinates'),
                        subtitle: Text(
                          '${incident.latitude.toStringAsFixed(6)}, ${incident.longitude.toStringAsFixed(6)}',
                        ),
                        contentPadding: EdgeInsets.zero,
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              if (incident.status.toUpperCase() == 'REPORTED')
                FilledButton.icon(
                  onPressed: () async {
                    await ref
                        .read(responderRepositoryProvider)
                        .updateIncidentStatus(incidentId, 'ACKNOWLEDGED');
                    ref.invalidate(
                        responderIncidentDetailProvider(incidentId));
                  },
                  icon: const Icon(Icons.check),
                  label: const Text('Accept Incident'),
                ),
              if (incident.status.toUpperCase() == 'ACKNOWLEDGED')
                FilledButton.icon(
                  onPressed: () async {
                    await ref
                        .read(responderRepositoryProvider)
                        .updateIncidentStatus(incidentId, 'RESPONDING');
                    ref.invalidate(
                        responderIncidentDetailProvider(incidentId));
                  },
                  icon: const Icon(Icons.directions_run),
                  label: const Text('Mark En Route'),
                  style: FilledButton.styleFrom(backgroundColor: Colors.orange),
                ),
              if (incident.status.toUpperCase() == 'RESPONDING')
                FilledButton.icon(
                  onPressed: () async {
                    await ref
                        .read(responderRepositoryProvider)
                        .updateIncidentStatus(incidentId, 'RESOLVED');
                    ref.invalidate(
                        responderIncidentDetailProvider(incidentId));
                  },
                  icon: const Icon(Icons.task_alt),
                  label: const Text('Mark Resolved'),
                  style: FilledButton.styleFrom(backgroundColor: Colors.green),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
