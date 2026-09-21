import 'package:flutter/material.dart';

import '../data/sachet_alert_model.dart';

/// Card widget displaying active NDMA SACHET CAP 1.2 disaster alerts
/// and civil defense emergency overrides on the tourist dashboard.
class SachetDisasterAlertCard extends StatelessWidget {
  const SachetDisasterAlertCard({
    super.key,
    required this.alert,
    required this.overrideActive,
  });

  final SachetAlertModel alert;
  final bool overrideActive;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Card(
      elevation: 4,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: overrideActive ? colorScheme.error : Colors.amber.shade800,
          width: 2,
        ),
      ),
      color: overrideActive
          ? colorScheme.errorContainer
          : Colors.amber.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Synthetic Test Fixture Watermark
            if (alert.isSynthetic) ...[
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.black87,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Text(
                  'TEST FIXTURE / SYNTHETIC — NOT AN AUTHENTIC GOVERNMENT ALERT',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 10,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],

            // Alert Header & Status Badge
            Row(
              children: [
                Icon(
                  overrideActive
                      ? Icons.warning_rounded
                      : Icons.info_outline_rounded,
                  color: overrideActive
                      ? colorScheme.onErrorContainer
                      : Colors.amber.shade900,
                  size: 28,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        overrideActive
                            ? 'DISASTER EMERGENCY OVERRIDE'
                            : 'CIVIL DEFENSE ADVISORY',
                        style: TextStyle(
                          color: overrideActive
                              ? colorScheme.onErrorContainer
                              : Colors.amber.shade900,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                          letterSpacing: 1.0,
                        ),
                      ),
                      Text(
                        alert.event,
                        style: TextStyle(
                          color: overrideActive
                              ? colorScheme.onErrorContainer
                              : Colors.black87,
                          fontWeight: FontWeight.bold,
                          fontSize: 18,
                        ),
                      ),
                    ],
                  ),
                ),
                if (overrideActive)
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: colorScheme.error,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: const Text(
                      'CRITICAL',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),

            // Severity, Urgency & Certainty Indicators
            Wrap(
              spacing: 6,
              children: [
                _Badge(label: 'Severity: ${alert.severity.toUpperCase()}'),
                _Badge(label: 'Urgency: ${alert.urgency.toUpperCase()}'),
                _Badge(label: 'Certainty: ${alert.certainty.toUpperCase()}'),
              ],
            ),
            const SizedBox(height: 12),

            // Headline
            if (alert.headline != null && alert.headline!.isNotEmpty) ...[
              Text(
                alert.headline!,
                style: const TextStyle(
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                ),
              ),
              const SizedBox(height: 6),
            ],

            // Instruction (Civil Defense Directives)
            if (alert.instruction != null && alert.instruction!.isNotEmpty) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.7),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: Colors.black12,
                  ),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.shield_outlined,
                        size: 20, color: Colors.black87),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        alert.instruction!,
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: Colors.black87,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
            ],

            // Expiration and Area
            Text(
              [
                if (alert.areaDesc != null) alert.areaDesc!,
                'Valid until ${alert.expiresAt.toLocal().toIso8601String().substring(0, 16).replaceAll('T', ' ')}',
              ].join(' · '),
              style: TextStyle(
                fontSize: 11,
                color: Colors.grey.shade700,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        label,
        style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w600),
      ),
    );
  }
}
