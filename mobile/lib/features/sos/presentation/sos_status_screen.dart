import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/di/providers.dart';
import '../data/sos_outbox_item.dart';
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
  Timer? _pollTimer;

  /// True when a network error occurred during refresh — shows a warning badge.
  bool _refreshNetworkWarning = false;

  /// True when the outbox transmission has permanently failed.
  bool _isTransmissionFailed = false;

  /// Error message associated with the failed transmission.
  String? _failedErrorMessage;

  /// Non-null when cancellation is queued offline (Case C — CANCEL_PENDING).
  String? _cancelPendingMessage;

  /// Non-null when cancellation was definitively refused (Case D — CANCELLATION_FAILED).
  String? _cancellationFailedMessage;

  @override
  void initState() {
    super.initState();
    _alert = widget.initialAlert;
    _checkOutboxStatus();
    _startPolling();
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  void _startPolling() {
    _pollTimer?.cancel();
    _pollTimer = Timer(const Duration(seconds: 15), () {
      if (mounted) {
        _refresh();
        _startPolling();
      }
    });
  }

  Future<void> _checkOutboxStatus() async {
    final targetKey = _alert.clientRequestId ?? _alert.sosId;
    final store = ref.read(sosRepositoryProvider).outboxStore;
    if (store == null) return;
    try {
      final outboxItem = await store.get(targetKey);
      if (!mounted) return;
      if (outboxItem != null && outboxItem.status == SosOutboxStatus.failed) {
        setState(() {
          _isTransmissionFailed = true;
          _failedErrorMessage = outboxItem.lastError ??
              'Automatic emergency transmission failed. Maximum attempts exhausted.';
        });
      } else if (outboxItem != null && outboxItem.status == SosOutboxStatus.pending) {
        setState(() {
          _isTransmissionFailed = false;
        });
      } else if (outboxItem == null && !_alert.isPendingDelivery) {
        setState(() {
          _isTransmissionFailed = false;
        });
      }
    } catch (_) {
      // Ignore lookup errors
    }
  }

  Future<void> _retryTransmission() async {
    final targetKey = _alert.clientRequestId ?? _alert.sosId;
    setState(() => _loading = true);
    try {
      await ref.read(sosRepositoryProvider).retryFailedAlert(targetKey);
      await _refresh();
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'Retry failed: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _refresh() async {
    try {
      if (_alert.isPendingDelivery) {
        await ref.read(sosRepositoryProvider).drainOutbox();
      }
      final active = await ref.read(sosRepositoryProvider).getActiveSos();
      if (!mounted) return;
      // Clear any prior network warning badge on a successful refresh.
      setState(() => _refreshNetworkWarning = false);
      if (active != null) {
        setState(() {
          _alert = active;
          _isTransmissionFailed = false;
        });
      } else if (_alert.status.isActive && !_alert.isPendingDelivery) {
        // Status may have become terminal on the server; pop back.
        if (mounted) Navigator.of(context).pop();
      }
      await _checkOutboxStatus();
    } on Exception {
      // Network or transient error: show a warning badge but NEVER pop the screen.
      // The tourist keeps seeing the last known state until connectivity is restored.
      if (!mounted) return;
      setState(() => _refreshNetworkWarning = true);
      await _checkOutboxStatus();
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
      _cancelPendingMessage = null;
      _cancellationFailedMessage = null;
    });
    try {
      final repo = ref.read(sosRepositoryProvider);
      await repo.reconcileAndCancelSos(_alert);
      if (!mounted) return;

      // Read back the outbox item to determine the outcome.
      final targetKey = _alert.clientRequestId ?? _alert.sosId;
      final outboxItem = await repo.outboxStore?.get(targetKey);

      if (!mounted) return;
      if (outboxItem == null) {
        // Case A or B — cancellation confirmed or SOS not found. Navigate back.
        setState(() => _loading = false);
        if (mounted) Navigator.of(context).pop();
      } else if (outboxItem.status == SosOutboxStatus.cancelPending) {
        // Case C — queued offline: show amber "Cancellation Pending" banner.
        setState(() {
          _loading = false;
          _cancelPendingMessage =
              'Cancellation Pending: Responders will be notified as soon as '
              'network is restored.';
        });
      } else if (outboxItem.status == SosOutboxStatus.cancellationFailed) {
        // Case D — definitively refused: show red "Cancellation Not Permitted" banner.
        final reason = outboxItem.lastError ?? 'Server refused the cancellation request.';
        setState(() {
          _loading = false;
          _cancellationFailedMessage =
              'Cancellation Not Permitted: $reason. '
              'Contact emergency services if needed.';
        });
      } else {
        // Unexpected outbox state — treat as success and pop.
        setState(() => _loading = false);
        if (mounted) Navigator.of(context).pop();
      }
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
    final isPendingOffline = _alert.isPendingDelivery;

    final (Color statusColor, IconData statusIcon, String statusTitle) = _isTransmissionFailed
        ? (colorScheme.error, Icons.error_outline_rounded, 'Transmission Failed')
        : isPendingOffline
            ? (Colors.orange.shade800, Icons.cloud_off_rounded, 'Pending Delivery (Offline)')
            : switch (status) {
                SosStatusValue.pending => (Colors.orange, Icons.access_time_rounded, status.displayLabel),
                SosStatusValue.acknowledged =>
                  (Colors.amber.shade800, Icons.notifications_active_rounded, status.displayLabel),
                SosStatusValue.responding => (Colors.blue, Icons.directions_run_rounded, status.displayLabel),
                SosStatusValue.resolved => (Colors.green, Icons.check_circle_rounded, status.displayLabel),
                SosStatusValue.cancelled => (Colors.grey, Icons.cancel_rounded, status.displayLabel),
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
                    statusTitle,
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
                      icon: _isTransmissionFailed
                          ? Icons.error_outline
                          : isPendingOffline
                              ? Icons.cloud_queue
                              : Icons.cloud_done,
                      label: 'Delivery state',
                      value: _isTransmissionFailed
                          ? 'Transmission Failed (Automatic delivery stopped)'
                          : isPendingOffline
                              ? 'Queued Locally (Awaiting network connection)'
                              : 'Delivered to GeoShield Server',
                    ),
                    const SizedBox(height: 8),
                    _DetailRow(
                      icon: Icons.fingerprint,
                      label: 'SOS ID',
                      value: _alert.sosId.length > 8
                          ? '…${_alert.sosId.substring(_alert.sosId.length - 8)}'
                          : _alert.sosId,
                    ),
                    if (_alert.clientRequestId != null) ...[
                      const SizedBox(height: 8),
                      _DetailRow(
                        icon: Icons.key_rounded,
                        label: 'Client Request ID',
                        value: _alert.clientRequestId!.length > 8
                            ? '…${_alert.clientRequestId!.substring(_alert.clientRequestId!.length - 8)}'
                            : _alert.clientRequestId!,
                      ),
                    ],
                    const SizedBox(height: 8),
                    _DetailRow(
                      icon: Icons.location_on_rounded,
                      label: 'Location',
                      value:
                          '${_alert.latitude.toStringAsFixed(5)}, ${_alert.longitude.toStringAsFixed(5)}',
                    ),
                    const SizedBox(height: 8),
                    _DetailRow(
                      icon: Icons.schedule_rounded,
                      label: 'Triggered at',
                      value: _formatTime(_alert.triggeredAt.toLocal()),
                    ),
                    if (_alert.acknowledgedAt != null) ...[
                      const SizedBox(height: 8),
                      _DetailRow(
                        icon: Icons.notifications_active_outlined,
                        label: 'Acknowledged at',
                        value: _formatTime(_alert.acknowledgedAt!.toLocal()),
                      ),
                    ],
                    if (_alert.respondingAt != null) ...[
                      const SizedBox(height: 8),
                      _DetailRow(
                        icon: Icons.directions_run_outlined,
                        label: 'Responding at',
                        value: _formatTime(_alert.respondingAt!.toLocal()),
                      ),
                    ],
                    if (_alert.resolvedAt != null) ...[
                      const SizedBox(height: 8),
                      _DetailRow(
                        icon: Icons.check_circle_outline,
                        label: 'Resolved at',
                        value: _formatTime(_alert.resolvedAt!.toLocal()),
                      ),
                    ],
                    if (_alert.cancelledAt != null) ...[
                      const SizedBox(height: 8),
                      _DetailRow(
                        icon: Icons.cancel_outlined,
                        label: 'Cancelled at',
                        value: _formatTime(_alert.cancelledAt!.toLocal()),
                      ),
                    ],
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

            // Transmission-failed banner — automatic delivery stopped
            if (_isTransmissionFailed) ...[
              Card(
                color: colorScheme.errorContainer,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.error_rounded,
                              color: colorScheme.onErrorContainer, size: 28),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Emergency Transmission Failed',
                              style: Theme.of(context)
                                  .textTheme
                                  .titleMedium
                                  ?.copyWith(
                                    color: colorScheme.onErrorContainer,
                                    fontWeight: FontWeight.bold,
                                  ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Automatic transmission has stopped.\n${_failedErrorMessage ?? "Emergency responders could not be reached."}',
                        style: TextStyle(color: colorScheme.onErrorContainer),
                      ),
                      const SizedBox(height: 16),
                      Row(
                        children: [
                          FilledButton.icon(
                            onPressed: () => launchUrl(Uri.parse('tel:112')),
                            style: FilledButton.styleFrom(
                              backgroundColor: colorScheme.error,
                              foregroundColor: colorScheme.onError,
                            ),
                            icon: const Icon(Icons.phone_rounded),
                            label: const Text('Call 112'),
                          ),
                          const SizedBox(width: 12),
                          OutlinedButton.icon(
                            onPressed: _loading ? null : _retryTransmission,
                            style: OutlinedButton.styleFrom(
                              foregroundColor: colorScheme.onErrorContainer,
                              side: BorderSide(
                                  color: colorScheme.onErrorContainer),
                            ),
                            icon: _loading
                                ? const SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2),
                                  )
                                : const Icon(Icons.refresh_rounded),
                            label: const Text('Retry Now'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Network refresh warning — shown when _refresh() fails (no network)
            if (_refreshNetworkWarning) ...[
              Card(
                color: Colors.amber.withValues(alpha: 0.12),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      const Icon(Icons.wifi_off_rounded, color: Colors.amber),
                      const SizedBox(width: 8),
                      const Expanded(
                        child: Text(
                          'Unable to refresh status. Showing last known state.',
                          style: TextStyle(color: Colors.black87),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],

            // Cancel-pending banner — Case C: cancel queued, awaiting network
            if (_cancelPendingMessage != null) ...[
              Card(
                color: Colors.amber.withValues(alpha: 0.15),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      const Icon(Icons.schedule_send_rounded, color: Colors.amber),
                      const SizedBox(width: 8),
                      Expanded(child: Text(_cancelPendingMessage!)),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],

            // Cancellation-failed banner — Case D: server rejected or ID mismatch
            if (_cancellationFailedMessage != null) ...[
              Card(
                color: colorScheme.errorContainer,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.cancel_rounded,
                              color: colorScheme.onErrorContainer),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _cancellationFailedMessage!,
                              style: TextStyle(
                                  color: colorScheme.onErrorContainer),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      TextButton.icon(
                        onPressed: () => launchUrl(Uri.parse('tel:112')),
                        icon: const Icon(Icons.phone_rounded),
                        label: const Text('Call 112'),
                        style: TextButton.styleFrom(
                            foregroundColor: colorScheme.onErrorContainer),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],

            // Show guidance message
            if (_isTransmissionFailed)
              const SizedBox.shrink()
            else if (isPendingOffline)
              Card(
                color: Colors.orange.withValues(alpha: 0.08),
                child: const Padding(
                  padding: EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Icon(Icons.info_outline, color: Colors.deepOrange),
                      SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          'Your device is currently offline. Your SOS has been securely saved '
                          'in your local outbox and will automatically transmit to responders '
                          'as soon as mobile data or Wi-Fi is restored.',
                        ),
                      ),
                    ],
                  ),
                ),
              )
            else if (status.isActive)
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
