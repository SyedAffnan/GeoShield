import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/di/providers.dart';
import '../../../risk/data/sachet_alert_model.dart';
import 'admin_sachet_detail_sheet.dart';

/// Screen listing all active NDMA SACHET disaster alerts for administrators.
class AdminSachetAlertsScreen extends ConsumerWidget {
  const AdminSachetAlertsScreen({super.key});

  Future<void> _handleCancelAlert(
    BuildContext context,
    WidgetRef ref,
    SachetAlertModel alert,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cancel SACHET Alert'),
        content: Text(
          'Are you sure you want to cancel the active disaster alert "${alert.identifier}" (${alert.event})?\n\n'
          'This will submit an authoritative CAP Cancel broadcast and remove the alert from active monitoring.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Keep Active'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(ctx).colorScheme.error,
            ),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Confirm Cancellation'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      final repo = ref.read(adminSachetRepositoryProvider);
      await repo.cancelAlert(alert);
      ref.invalidate(adminActiveSachetAlertsProvider);
      if (context.mounted) {
        // Pop the detail sheet if it was open
        Navigator.of(context).maybePop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: Colors.green.shade800,
            content: Text('SACHET alert "${alert.identifier}" cancelled successfully.'),
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: Colors.red.shade800,
            content: Text('Failed to cancel alert: $e'),
          ),
        );
      }
    }
  }

  void _showDetailSheet(
    BuildContext context,
    WidgetRef ref,
    SachetAlertModel alert,
  ) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => AdminSachetDetailSheet(
        alert: alert,
        onCancelAlert: () => _handleCancelAlert(context, ref, alert),
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sessionAsync = ref.watch(authControllerProvider);
    final user = sessionAsync.asData?.value;

    // Defense-in-depth role verification
    if (user != null && !user.isAdmin) {
      return Scaffold(
        appBar: AppBar(title: const Text('Access Denied')),
        body: const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'Administrator authorization is required to access SACHET disaster alert management.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      );
    }

    final alertsAsync = ref.watch(adminActiveSachetAlertsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('SACHET Alerts'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(adminActiveSachetAlertsProvider),
          ),
        ],
      ),
      body: alertsAsync.when(
        loading: () => const Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Loading active disaster alerts...'),
            ],
          ),
        ),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, size: 48, color: Colors.red),
                const SizedBox(height: 16),
                Text(
                  'Failed to load active disaster alerts.\n$error',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: () =>
                      ref.invalidate(adminActiveSachetAlertsProvider),
                  icon: const Icon(Icons.refresh),
                  label: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
        data: (alerts) => RefreshIndicator(
          onRefresh: () async =>
              ref.invalidate(adminActiveSachetAlertsProvider),
          child: alerts.isEmpty
              ? _buildEmptyState(context)
              : ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: alerts.length,
                  itemBuilder: (context, index) {
                    final alert = alerts[index];
                    return _AlertCard(
                      alert: alert,
                      onTap: () => _showDetailSheet(context, ref, alert),
                      onCancel: () => _handleCancelAlert(context, ref, alert),
                    );
                  },
                ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          await context.push('/admin/sachet/create');
          ref.invalidate(adminActiveSachetAlertsProvider);
        },
        icon: const Icon(Icons.add_alert),
        label: const Text('Broadcast Alert'),
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(32),
      children: [
        const SizedBox(height: 60),
        Icon(
          Icons.shield_outlined,
          size: 72,
          color: Colors.green.shade600,
        ),
        const SizedBox(height: 16),
        Text(
          'No Active Disaster Alerts',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),
        const SizedBox(height: 8),
        Text(
          'There are currently no active, unexpired NDMA SACHET disaster alerts broadcasted in the system.',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Colors.grey.shade600,
              ),
        ),
        const SizedBox(height: 24),
        Center(
          child: OutlinedButton.icon(
            onPressed: () => context.push('/admin/sachet/create'),
            icon: const Icon(Icons.broadcast_on_personal),
            label: const Text('Broadcast New Alert'),
          ),
        ),
      ],
    );
  }
}

class _AlertCard extends StatelessWidget {
  const _AlertCard({
    required this.alert,
    required this.onTap,
    required this.onCancel,
  });

  final SachetAlertModel alert;
  final VoidCallback onTap;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isExtreme = alert.severity.toUpperCase() == 'EXTREME';
    final isSevere = alert.severity.toUpperCase() == 'SEVERE';

    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      elevation: 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: isExtreme
              ? Colors.red.shade700
              : isSevere
                  ? Colors.orange.shade700
                  : theme.dividerColor,
          width: isExtreme || isSevere ? 1.5 : 1.0,
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top row: Synthetic watermark or category
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  if (alert.isSynthetic)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.black87,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: const Text(
                        'TEST DRILL',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    )
                  else
                    Text(
                      alert.category.toUpperCase(),
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: colorScheme.primary,
                        letterSpacing: 0.8,
                      ),
                    ),
                  Text(
                    'Expires: ${_formatShortDate(alert.expiresAt)}',
                    style: TextStyle(
                      fontSize: 11,
                      color: Colors.grey.shade600,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),

              // Title and headline
              Text(
                alert.event,
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              if (alert.headline != null && alert.headline!.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  alert.headline!,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: Colors.grey.shade700,
                  ),
                ),
              ],
              const SizedBox(height: 12),

              // Severity, Urgency, Certainty badges
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  _Badge(
                    label: alert.severity.toUpperCase(),
                    color: _severityColor(alert.severity),
                  ),
                  _Badge(
                    label: alert.urgency.toUpperCase(),
                    color: _urgencyColor(alert.urgency),
                  ),
                  if (alert.status != null)
                    _Badge(
                      label: alert.status!.toUpperCase(),
                      color: Colors.blueGrey,
                    ),
                ],
              ),
              const SizedBox(height: 12),

              // Area & Identifier footer
              Row(
                children: [
                  Icon(Icons.place_outlined, size: 14, color: Colors.grey.shade600),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      alert.areaDesc ?? 'Affected geographic area',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                    ),
                  ),
                  Text(
                    alert.identifier,
                    style: TextStyle(
                      fontSize: 10,
                      fontFamily: 'monospace',
                      color: Colors.grey.shade500,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  static String _formatShortDate(DateTime dt) {
    final local = dt.toLocal();
    final m = local.month.toString().padLeft(2, '0');
    final d = local.day.toString().padLeft(2, '0');
    final h = local.hour.toString().padLeft(2, '0');
    final min = local.minute.toString().padLeft(2, '0');
    return '$d/$m $h:$min';
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

class _Badge extends StatelessWidget {
  const _Badge({required this.label, required this.color});
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
