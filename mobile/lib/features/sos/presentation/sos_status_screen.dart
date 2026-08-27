import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../data/sos_repository.dart';

/// Displays the tourist's currently active SOS alert and lets them cancel it.
/// Auto-refreshes every 15 seconds while the screen is open so the tourist
/// sees status updates from the responder in near-real-time.
class SosStatusScreen extends ConsumerStatefulWidget {
  const SosStatusScreen({super.key, required this.initialAlert});
  final SosAlert initialAlert;

  @override
  ConsumerState<SosStatusScreen> createState() => _SosStatusScreenState();
}

class _SosStatusScreenState extends ConsumerState<SosStatusScreen> {
  late SosAlert _alert;
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _alert = widget.initialAlert;
    _startPolling();
  }

  void _startPolling() {
    Future.delayed(const Duration(seconds: 15), () {
      if (mounted) {
        _refresh();
        _startPolling();
      }
    });
  }

  Future<void> _refresh() async {
    try {
      final active = await ref.read(sosRepositoryProvider).getActiveSos();
      if (!mounted) return;
      if (active != null) {
        setState(() => _alert = active);
      } else if (_alert.status.isActive) {
        // Status may have become terminal on the server; pop back.
        if (mounted) Navigator.of(context).pop();
      }
    } catch (_) {
      // Silently swallow poll errors — the UI retains the last known state.
    }
  }

  Future<void> _cancelSos() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Cancel SOS?'),
        content: const Text(
            'Are you sure you want to cancel your emergency SOS alert? '
            'This will notify the responder that help is no longer needed.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Keep SOS'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error),
            child: const Text('Cancel SOS'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final updated =
          await ref.read(sosRepositoryProvider).cancelSos(_alert.sosId);
      if (!mounted) return;
      setState(() => _alert = updated);
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'Could not cancel: ${e.toString()}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = _alert.status;
    final colorScheme = Theme.of(context).colorScheme;

    final (Color statusColor, IconData statusIcon) = switch (status) {
      SosStatusValue.pending => (Colors.orange, Icons.access_time_rounded),
      SosStatusValue.acknowledged =>
        (Colors.amber.shade800, Icons.notifications_active_rounded),
      SosStatusValue.responding => (Colors.blue, Icons.directions_run_rounded),
      SosStatusValue.resolved => (Colors.green, Icons.check_circle_rounded),
      SosStatusValue.cancelled => (Colors.grey, Icons.cancel_rounded),
    };

    return Scaffold(
      appBar: AppBar(
        title: const Text('Emergency SOS'),
        backgroundColor: colorScheme.errorContainer,
        foregroundColor: colorScheme.onErrorContainer,
        actions: [
          IconButton(
            tooltip: 'Refresh status',
            icon: const Icon(Icons.refresh),
            onPressed: _refresh,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            // Status banner
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: statusColor.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: statusColor.withValues(alpha: 0.4)),
              ),
              child: Column(
                children: [
                  Icon(statusIcon, size: 56, color: statusColor),
                  const SizedBox(height: 16),
                  Text(
                    status.displayLabel,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          color: statusColor,
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Alert details
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Alert Details',
                        style: Theme.of(context).textTheme.titleMedium),
                    const Divider(height: 24),
                    _DetailRow(
                      icon: Icons.fingerprint,
                      label: 'SOS ID',
                      value: _alert.sosId.length > 8
                          ? '…${_alert.sosId.substring(_alert.sosId.length - 8)}'
                          : _alert.sosId,
                    ),
                    const SizedBox(height: 8),
                    _DetailRow(
                      icon: Icons.location_on_rounded,
                      label: 'Location sent',
                      value:
                          '${_alert.latitude.toStringAsFixed(5)}, ${_alert.longitude.toStringAsFixed(5)}',
                    ),
                    const SizedBox(height: 8),
                    _DetailRow(
                      icon: Icons.schedule_rounded,
                      label: 'Triggered at',
                      value: _formatTime(_alert.triggeredAt.toLocal()),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            if (_error != null) ...[
              Card(
                color: colorScheme.errorContainer,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(_error!,
                      style: TextStyle(color: colorScheme.onErrorContainer)),
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Show guidance message
            if (status.isActive)
              Card(
                color: Colors.blue.withValues(alpha: 0.08),
                child: const Padding(
                  padding: EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Icon(Icons.info_outline, color: Colors.blue),
                      SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          'GeoShield emergency responders have received your alert. '
                          'Stay in a safe location if possible and keep your phone '
                          'accessible.',
                        ),
                      ),
                    ],
                  ),
                ),
              ),

            const SizedBox(height: 32),

            // Actions
            if (status.isActive) ...[
              OutlinedButton.icon(
                onPressed: _loading ? null : _cancelSos,
                icon: _loading
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.cancel_outlined),
                label: const Text('Cancel SOS'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: colorScheme.error,
                  side: BorderSide(color: colorScheme.error),
                ),
              ),
            ] else ...[
              FilledButton.icon(
                onPressed: () => Navigator.of(context).pop(),
                icon: const Icon(Icons.arrow_back),
                label: const Text('Back to Dashboard'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _formatTime(DateTime dt) =>
      '${dt.year}-${_pad(dt.month)}-${_pad(dt.day)} '
      '${_pad(dt.hour)}:${_pad(dt.minute)}:${_pad(dt.second)}';

  String _pad(int n) => n.toString().padLeft(2, '0');
}

class _DetailRow extends StatelessWidget {
  const _DetailRow(
      {required this.icon, required this.label, required this.value});
  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 8),
          Expanded(
            child: RichText(
              text: TextSpan(
                style: Theme.of(context).textTheme.bodyMedium,
                children: [
                  TextSpan(
                      text: '$label: ',
                      style:
                          const TextStyle(fontWeight: FontWeight.w600)),
                  TextSpan(text: value),
                ],
              ),
            ),
          ),
        ],
      );
}
