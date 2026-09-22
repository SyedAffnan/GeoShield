import 'package:flutter/material.dart';

import '../data/historical_advisory_model.dart';
import 'model_provenance_sheet.dart';

/// Distinct slate-styled card presenting the retrospective 2024
/// Historical Trend Advisory.
///
/// Strictly informational and separated from authoritative risk scoring.
class HistoricalTrendAdvisoryCard extends StatelessWidget {
  const HistoricalTrendAdvisoryCard({super.key, required this.advisory});

  final HistoricalTrendAdvisoryModel advisory;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    // Strict muted slate aesthetic — NEVER red/green/amber risk badges
    final cardBg = isDark ? const Color(0xFF1E242B) : const Color(0xFFF1F5F9);
    final cardBorder = isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1);
    final slateHeader = isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569);
    final textDark = isDark ? const Color(0xFFF8FAFC) : const Color(0xFF0F172A);

    if (!advisory.isAvailable) {
      return Card(
        color: cardBg,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: BorderSide(color: cardBorder, width: 1.5),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.history, color: slateHeader, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    'Historical Trend Advisory',
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: slateHeader,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                advisory.advisoryNotice.isNotEmpty
                    ? advisory.advisoryNotice
                    : 'Historical trend advisory is currently unavailable for this region.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: isDark ? Colors.grey.shade400 : Colors.grey.shade700,
                ),
              ),
            ],
          ),
        ),
      );
    }

    final regionName = advisory.geographicUnit ?? 'India';
    final severityStr = advisory.predictedAccidentSeverity != null
        ? advisory.predictedAccidentSeverity!.toStringAsFixed(1)
        : '--';

    return Card(
      color: cardBg,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: cardBorder, width: 1.5),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Card Header
            Row(
              children: [
                Icon(Icons.auto_graph_outlined, color: slateHeader, size: 22),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Historical Trend Advisory',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: textDark,
                        ),
                      ),
                      Text(
                        'Retrospective 2024 regional transport-accident severity indicator',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: slateHeader,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // Regional Indicator & Metric Display
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF0F172A) : Colors.white,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'STATE / UT: ${regionName.toUpperCase()}',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 0.5,
                          color: slateHeader,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          '2024 HOLDOUT',
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            color: slateHeader,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Text(
                        severityStr,
                        style: theme.textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: textDark,
                          fontFamily: 'monospace',
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'fatalities per 100 accidents (model-estimated)',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: slateHeader,
                          ),
                        ),
                      ),
                    ],
                  ),
                  if (advisory.advisoryNotice.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(
                      advisory.advisoryNotice,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: isDark ? Colors.grey.shade300 : Colors.grey.shade700,
                        fontStyle: FontStyle.italic,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 12),

            // Mandatory Scope Disclaimer Box
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: isDark
                    ? const Color(0xFF1E293B).withValues(alpha: 0.6)
                    : const Color(0xFFE2E8F0).withValues(alpha: 0.6),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(
                  color: isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1),
                ),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.info_outline,
                    size: 16,
                    color: slateHeader,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      advisory.scopeDisclaimer.isNotEmpty
                          ? advisory.scopeDisclaimer
                          : 'This is a retrospective 2024 model-evaluation result for regional road-accident severity. '
                              'It is not a current condition, forecast, tourist-safety assessment, or GeoShield Safety Score.',
                      style: TextStyle(
                        fontSize: 11,
                        height: 1.35,
                        color: slateHeader,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Model Provenance Button
            if (advisory.provenance != null) ...[
              const SizedBox(height: 12),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () => ModelProvenanceSheet.show(
                    context,
                    advisory.provenance!,
                  ),
                  icon: const Icon(Icons.history_edu, size: 16),
                  label: const Text('View Model Provenance'),
                  style: TextButton.styleFrom(
                    foregroundColor: slateHeader,
                    textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
