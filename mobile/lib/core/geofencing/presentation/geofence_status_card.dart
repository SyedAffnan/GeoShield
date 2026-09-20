import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/geofence_zone.dart';
import 'geofence_controller.dart';
import '../../di/providers.dart';

/// Minimal, battery-friendly status card displaying current geofence operational state.
/// Strictly decoupled from risk formula and safety score factors.
class GeofenceStatusCard extends ConsumerWidget {
  const GeofenceStatusCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final geofenceState = ref.watch(geofenceControllerProvider);

    if (geofenceState is! GeofenceReady) {
      return const SizedBox.shrink();
    }

    final (IconData icon, Color color, String title, String subtitle) =
        switch (geofenceState.activeZone) {
      GeofenceZone.core => (
          Icons.warning_rounded,
          Theme.of(context).colorScheme.error,
          'Inside designated hazard zone',
          geofenceState.nearestHazardName != null
              ? '${geofenceState.isSynthetic ? '[Test Fixture] ' : ''}${geofenceState.nearestHazardName} (${geofenceState.nearestDistanceMeters?.toStringAsFixed(0)} m)'
              : 'Core hazard boundary active',
        ),
      GeofenceZone.preWarning => (
          Icons.info_outline,
          Colors.amber.shade800,
          'Approaching hazard zone',
          geofenceState.nearestHazardName != null
              ? '${geofenceState.isSynthetic ? '[Test Fixture] ' : ''}${geofenceState.nearestHazardName} (${geofenceState.nearestDistanceMeters?.toStringAsFixed(0)} m)'
              : 'Informational pre-warning boundary active',
        ),
      GeofenceZone.outside => (
          Icons.shield_outlined,
          Colors.green,
          'No active hazard nearby',
          'Autonomous geofence monitoring active',
        ),
    };

    return Card(
      color: color.withValues(alpha: 0.08),
      child: ListTile(
        leading: Icon(icon, color: color),
        title: Row(
          children: [
            Expanded(
              child: Text(
                title,
                style: TextStyle(fontWeight: FontWeight.bold, color: color),
              ),
            ),
            if (geofenceState.isSynthetic &&
                geofenceState.activeZone != GeofenceZone.outside)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.purple.shade50,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: Colors.purple.shade300),
                ),
                child: Text(
                  'Test Fixture',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: Colors.purple.shade800,
                  ),
                ),
              ),
          ],
        ),
        subtitle: Text(subtitle),
      ),
    );
  }
}
