import 'package:flutter/material.dart';
import '../../../risk/data/sachet_alert_model.dart';

/// Modal bottom sheet presenting the comprehensive details of an NDMA SACHET alert.
class AdminSachetDetailSheet extends StatelessWidget {
  const AdminSachetDetailSheet({
    super.key,
    required this.alert,
    this.onCancelAlert,
  });

  final SachetAlertModel alert;
  final VoidCallback? onCancelAlert;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Container(
      decoration: BoxDecoration(
        color: theme.scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Center(
            child: Container(
              margin: const EdgeInsets.only(top: 12, bottom: 8),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade400,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            child: Row(
              children: [
                Icon(
                  Icons.campaign_outlined,
                  color: colorScheme.primary,
                  size: 28,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'SACHET Alert Details',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        alert.identifier,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: Colors.grey.shade600,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Flexible(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Synthetic alert watermark
                  if (alert.isSynthetic) ...[
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 6,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.black87,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: const Text(
                        'SYNTHETIC / TEST DRILL FIXTURE',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 11,
                          letterSpacing: 0.5,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],

                  // Event Title & Headline
                  Text(
                    alert.event,
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  if (alert.headline != null && alert.headline!.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      alert.headline!,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: Colors.grey.shade700,
                        fontStyle: FontStyle.italic,
                      ),
                    ),
                  ],
                  const SizedBox(height: 16),

                  // Core CAP metadata grid
                  _MetadataGrid(alert: alert),
                  const SizedBox(height: 16),

                  // Temporal validity
                  _SectionHeader(title: 'Temporal Validity'),
                  const SizedBox(height: 8),
                  _DetailRow(
                    label: 'Sent At',
                    value: _formatTimestamp(alert.sentAt),
                  ),
                  _DetailRow(
                    label: 'Effective',
                    value: _formatTimestamp(alert.effectiveAt),
                  ),
                  _DetailRow(
                    label: 'Expires',
                    value: _formatTimestamp(alert.expiresAt),
                  ),
                  const SizedBox(height: 16),

                  // Geographic scope
                  _SectionHeader(title: 'Geographic Scope'),
                  const SizedBox(height: 8),
                  _DetailRow(
                    label: 'Area Description',
                    value: alert.areaDesc ?? 'Not specified',
                  ),
                  const SizedBox(height: 16),

                  // Instruction
                  if (alert.instruction != null &&
                      alert.instruction!.isNotEmpty) ...[
                    _SectionHeader(title: 'Civil Defense Instruction'),
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        alert.instruction!,
                        style: theme.textTheme.bodyMedium,
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],

                  // Description
                  if (alert.description != null &&
                      alert.description!.isNotEmpty) ...[
                    _SectionHeader(title: 'Detailed Description'),
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        alert.description!,
                        style: theme.textTheme.bodyMedium,
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],

                  // Cancellation action
                  if (onCancelAlert != null) ...[
                    const SizedBox(height: 8),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: colorScheme.error,
                          side: BorderSide(color: colorScheme.error),
                          padding: const EdgeInsets.symmetric(vertical: 14),
                        ),
                        onPressed: onCancelAlert,
                        icon: const Icon(Icons.cancel_outlined),
                        label: const Text('Cancel Broadcast Alert'),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  static String _formatTimestamp(DateTime dt) {
    final local = dt.toLocal();
    final y = local.year.toString().padLeft(4, '0');
    final m = local.month.toString().padLeft(2, '0');
    final d = local.day.toString().padLeft(2, '0');
    final h = local.hour.toString().padLeft(2, '0');
    final min = local.minute.toString().padLeft(2, '0');
    return '$y-$m-$d $h:$min';
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title});
  final String title;

  @override
  Widget build(BuildContext context) {
    return Text(
      title,
      style: Theme.of(context).textTheme.titleSmall?.copyWith(
            fontWeight: FontWeight.bold,
            color: Theme.of(context).colorScheme.primary,
          ),
    );
  }
}

class _MetadataGrid extends StatelessWidget {
  const _MetadataGrid({required this.alert});
  final SachetAlertModel alert;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        _BadgePill(
          label: 'Severity: ${alert.severity}',
          color: _severityColor(alert.severity),
        ),
        _BadgePill(
          label: 'Urgency: ${alert.urgency}',
          color: _urgencyColor(alert.urgency),
        ),
        _BadgePill(
          label: 'Certainty: ${alert.certainty}',
          color: Colors.blueGrey,
        ),
        _BadgePill(
          label: 'Category: ${alert.category}',
          color: Colors.indigo,
        ),
        if (alert.status != null)
          _BadgePill(
            label: 'Status: ${alert.status}',
            color: Colors.teal,
          ),
        if (alert.msgType != null)
          _BadgePill(
            label: 'Msg: ${alert.msgType}',
            color: Colors.deepPurple,
          ),
      ],
    );
  }

  Color _severityColor(String severity) {
    switch (severity.toUpperCase()) {
      case 'EXTREME':
        return Colors.red.shade800;
      case 'SEVERE':
        return Colors.orange.shade800;
      case 'MODERATE':
        return Colors.amber.shade800;
      case 'MINOR':
        return Colors.blue.shade700;
      default:
        return Colors.grey.shade700;
    }
  }

  Color _urgencyColor(String urgency) {
    switch (urgency.toUpperCase()) {
      case 'IMMEDIATE':
        return Colors.red.shade700;
      case 'EXPECTED':
        return Colors.orange.shade700;
      case 'FUTURE':
        return Colors.blue.shade700;
      default:
        return Colors.grey.shade700;
    }
  }
}

class _BadgePill extends StatelessWidget {
  const _BadgePill({required this.label, required this.color});
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 130,
            child: Text(
              label,
              style: TextStyle(
                fontSize: 13,
                color: Colors.grey.shade600,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
