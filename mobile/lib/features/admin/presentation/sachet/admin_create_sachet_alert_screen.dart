import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/di/providers.dart';
import '../../data/admin_sachet_repository.dart';

/// Screen allowing administrators to compose and submit NDMA SACHET CAP 1.2 disaster alerts.
class AdminCreateSachetAlertScreen extends ConsumerStatefulWidget {
  const AdminCreateSachetAlertScreen({super.key});

  @override
  ConsumerState<AdminCreateSachetAlertScreen> createState() =>
      _AdminCreateSachetAlertScreenState();
}

class _AdminCreateSachetAlertScreenState
    extends ConsumerState<AdminCreateSachetAlertScreen> {
  final _formKey = GlobalKey<FormState>();

  // Form Controllers
  final _identifierController = TextEditingController();
  final _senderController = TextEditingController();
  final _eventController = TextEditingController();
  final _headlineController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _instructionController = TextEditingController();
  final _areaDescController = TextEditingController();
  final _circleController = TextEditingController(text: '11.0168,76.9558 15.0');
  final _polygonController = TextEditingController();

  // Dropdown States
  String _status = 'Actual';
  String _msgType = 'Alert';
  String _category = 'Met';
  String _urgency = 'Immediate';
  String _severity = 'Extreme';
  String _certainty = 'Observed';
  String _geometryType = 'CIRCLE'; // CIRCLE or POLYGON

  final DateTime _effectiveAt = DateTime.now();
  DateTime _expiresAt = DateTime.now().add(const Duration(hours: 24));

  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    // Generate suggested identifier
    final now = DateTime.now();
    _identifierController.text =
        'NDMA-${now.year}-ALERT-${now.millisecondsSinceEpoch.toString().substring(7)}';
  }

  @override
  void dispose() {
    _identifierController.dispose();
    _senderController.dispose();
    _eventController.dispose();
    _headlineController.dispose();
    _descriptionController.dispose();
    _instructionController.dispose();
    _areaDescController.dispose();
    _circleController.dispose();
    _polygonController.dispose();
    super.dispose();
  }

  bool get _isQualifyingSevereAlert {
    final s = _severity.toUpperCase();
    final u = _urgency.toUpperCase();
    return (s == 'EXTREME' || s == 'SEVERE') &&
        (u == 'IMMEDIATE' || u == 'EXPECTED');
  }

  Future<void> _handleConfirmAndSubmit() async {
    if (!_formKey.currentState!.validate()) return;

    final capXmlPayload = AdminSachetRepository.buildCapXml(
      identifier: _identifierController.text.trim(),
      sender: _senderController.text.trim(),
      sentAt: DateTime.now(),
      status: _status,
      msgType: _msgType,
      category: _category,
      event: _eventController.text.trim(),
      urgency: _urgency,
      severity: _severity,
      certainty: _certainty,
      effectiveAt: _effectiveAt,
      expiresAt: _expiresAt,
      headline: _headlineController.text.trim().isNotEmpty
          ? _headlineController.text.trim()
          : null,
      description: _descriptionController.text.trim().isNotEmpty
          ? _descriptionController.text.trim()
          : null,
      instruction: _instructionController.text.trim().isNotEmpty
          ? _instructionController.text.trim()
          : null,
      areaDesc: _areaDescController.text.trim().isNotEmpty
          ? _areaDescController.text.trim()
          : null,
      circle: _geometryType == 'CIRCLE' ? _circleController.text.trim() : null,
      polygon:
          _geometryType == 'POLYGON' ? _polygonController.text.trim() : null,
    );

    // Show Confirmation Dialog
    final confirmed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => _SubmissionConfirmationDialog(
        event: _eventController.text.trim(),
        severity: _severity,
        urgency: _urgency,
        areaDesc: _areaDescController.text.trim(),
        expiresAt: _expiresAt,
        isQualifyingOverride: _isQualifyingSevereAlert,
      ),
    );

    if (confirmed != true || !mounted) return;

    setState(() => _isSubmitting = true);

    try {
      final repo = ref.read(adminSachetRepositoryProvider);
      final ingested = await repo.ingestAlertXml(capXmlPayload);

      ref.invalidate(adminActiveSachetAlertsProvider);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: Colors.green.shade800,
            content: Text(
              'SACHET alert "${ingested.identifier}" broadcasted successfully.',
            ),
          ),
        );
        Navigator.of(context).pop(true);
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: Colors.red.shade800,
            content: Text('Failed to submit SACHET alert: $error'),
            duration: const Duration(seconds: 5),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Broadcast SACHET Alert'),
      ),
      body: _isSubmitting
          ? const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CircularProgressIndicator(),
                  SizedBox(height: 16),
                  Text('Ingesting and broadcasting CAP alert...'),
                ],
              ),
            )
          : _buildForm(theme),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: FilledButton.icon(
            onPressed: _isSubmitting ? null : _handleConfirmAndSubmit,
            icon: const Icon(Icons.broadcast_on_personal),
            label: const Text('Verify & Submit Alert'),
            style: FilledButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 16),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildForm(ThemeData theme) {
    return Form(
      key: _formKey,
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Severe Alert Warning banner
            if (_isQualifyingSevereAlert) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: theme.brightness == Brightness.dark
                      ? Colors.red.shade900.withValues(alpha: 0.25)
                      : Colors.red.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: theme.brightness == Brightness.dark
                        ? Colors.red.shade800
                        : Colors.red.shade400,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.warning_amber_rounded,
                      color: theme.brightness == Brightness.dark
                          ? Colors.red.shade300
                          : Colors.red.shade800,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        'Qualifying Emergency: Extreme/Severe severity with Immediate/Expected urgency triggers high-priority disaster override in GeoShield.',
                        style: TextStyle(
                          color: theme.brightness == Brightness.dark
                              ? Colors.red.shade100
                              : Colors.red.shade900,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            Text('Alert Metadata', style: theme.textTheme.titleMedium),
            const SizedBox(height: 12),
            TextFormField(
              controller: _identifierController,
              decoration: const InputDecoration(
                labelText: 'Identifier *',
                hintText: 'e.g. NDMA-2026-CYC-0042',
                border: OutlineInputBorder(),
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Identifier is required' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _senderController,
              decoration: const InputDecoration(
                labelText: 'Sender *',
                hintText: 'e.g. imd_alert@sachet.ndma.gov.in',
                border: OutlineInputBorder(),
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Sender is required' : null,
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: DropdownButtonFormField<String>(
                    initialValue: _status,
                    decoration: const InputDecoration(
                      labelText: 'Status',
                      border: OutlineInputBorder(),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'Actual', child: Text('Actual')),
                      DropdownMenuItem(value: 'Test', child: Text('Test')),
                      DropdownMenuItem(value: 'Exercise', child: Text('Exercise')),
                      DropdownMenuItem(value: 'Draft', child: Text('Draft')),
                    ],
                    onChanged: (v) => setState(() => _status = v!),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: DropdownButtonFormField<String>(
                    initialValue: _msgType,
                    decoration: const InputDecoration(
                      labelText: 'Message Type',
                      border: OutlineInputBorder(),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'Alert', child: Text('Alert')),
                      DropdownMenuItem(value: 'Update', child: Text('Update')),
                      DropdownMenuItem(value: 'Cancel', child: Text('Cancel')),
                    ],
                    onChanged: (v) => setState(() => _msgType = v!),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),

            Text('Classification & Event', style: theme.textTheme.titleMedium),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _category,
              decoration: const InputDecoration(
                labelText: 'Category',
                border: OutlineInputBorder(),
              ),
              items: const [
                DropdownMenuItem(value: 'Met', child: Text('Meteorological (Met)')),
                DropdownMenuItem(value: 'Geo', child: Text('Geological (Geo)')),
                DropdownMenuItem(value: 'Safety', child: Text('General Safety')),
                DropdownMenuItem(value: 'Security', child: Text('Law Enforcement / Security')),
                DropdownMenuItem(value: 'Rescue', child: Text('Rescue / Recovery')),
                DropdownMenuItem(value: 'Fire', child: Text('Fire Suppression')),
                DropdownMenuItem(value: 'Health', child: Text('Medical / Public Health')),
                DropdownMenuItem(value: 'Env', child: Text('Environmental')),
                DropdownMenuItem(value: 'Transport', child: Text('Transportation')),
                DropdownMenuItem(value: 'Infra', child: Text('Infrastructure')),
                DropdownMenuItem(value: 'Other', child: Text('Other')),
              ],
              onChanged: (v) => setState(() => _category = v!),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _eventController,
              decoration: const InputDecoration(
                labelText: 'Event Designation *',
                hintText: 'e.g. Severe Cyclonic Storm, Flash Flood Warning',
                border: OutlineInputBorder(),
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Event designation is required' : null,
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: DropdownButtonFormField<String>(
                    initialValue: _severity,
                    decoration: const InputDecoration(
                      labelText: 'Severity',
                      border: OutlineInputBorder(),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'Extreme', child: Text('Extreme')),
                      DropdownMenuItem(value: 'Severe', child: Text('Severe')),
                      DropdownMenuItem(value: 'Moderate', child: Text('Moderate')),
                      DropdownMenuItem(value: 'Minor', child: Text('Minor')),
                      DropdownMenuItem(value: 'Unknown', child: Text('Unknown')),
                    ],
                    onChanged: (v) => setState(() => _severity = v!),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: DropdownButtonFormField<String>(
                    initialValue: _urgency,
                    decoration: const InputDecoration(
                      labelText: 'Urgency',
                      border: OutlineInputBorder(),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'Immediate', child: Text('Immediate')),
                      DropdownMenuItem(value: 'Expected', child: Text('Expected')),
                      DropdownMenuItem(value: 'Future', child: Text('Future')),
                      DropdownMenuItem(value: 'Past', child: Text('Past')),
                      DropdownMenuItem(value: 'Unknown', child: Text('Unknown')),
                    ],
                    onChanged: (v) => setState(() => _urgency = v!),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _certainty,
              decoration: const InputDecoration(
                labelText: 'Certainty',
                border: OutlineInputBorder(),
              ),
              items: const [
                DropdownMenuItem(value: 'Observed', child: Text('Observed')),
                DropdownMenuItem(value: 'Likely', child: Text('Likely')),
                DropdownMenuItem(value: 'Possible', child: Text('Possible')),
                DropdownMenuItem(value: 'Unlikely', child: Text('Unlikely')),
                DropdownMenuItem(value: 'Unknown', child: Text('Unknown')),
              ],
              onChanged: (v) => setState(() => _certainty = v!),
            ),
            const SizedBox(height: 20),

            Text('Content & Directives', style: theme.textTheme.titleMedium),
            const SizedBox(height: 12),
            TextFormField(
              controller: _headlineController,
              decoration: const InputDecoration(
                labelText: 'Headline',
                hintText: 'Brief summary of the emergency broadcast',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _instructionController,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Civil Defense Instruction',
                hintText: 'Immediate protective actions for tourists and public',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _descriptionController,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Detailed Description',
                hintText: 'Extended synopsis or meteorological context',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 20),

            Text('Geographic Scope', style: theme.textTheme.titleMedium),
            const SizedBox(height: 12),
            TextFormField(
              controller: _areaDescController,
              decoration: const InputDecoration(
                labelText: 'Area Description',
                hintText: 'e.g. Coastal Odisha, Puri and Jagatsinghpur districts',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: SegmentedButton<String>(
                segments: const [
                  ButtonSegment(
                    value: 'CIRCLE',
                    label: Text('Circular Area'),
                    icon: Icon(Icons.circle_outlined),
                  ),
                  ButtonSegment(
                    value: 'POLYGON',
                    label: Text('Polygon Boundary'),
                    icon: Icon(Icons.polyline_outlined),
                  ),
                ],
                selected: {_geometryType},
                onSelectionChanged: (set) =>
                    setState(() => _geometryType = set.first),
              ),
            ),
            const SizedBox(height: 12),
            if (_geometryType == 'CIRCLE') ...[
              TextFormField(
                controller: _circleController,
                decoration: const InputDecoration(
                  labelText: 'Circle Coordinates (lat,lon radiusKm) *',
                  hintText: 'e.g. 11.0168,76.9558 15.0',
                  border: OutlineInputBorder(),
                ),
                validator: (v) => (v == null || v.trim().isEmpty)
                    ? 'Circle coordinates required'
                    : null,
              ),
            ] else ...[
              TextFormField(
                controller: _polygonController,
                maxLines: 2,
                decoration: const InputDecoration(
                  labelText: 'Polygon Coordinates (space-delimited pairs) *',
                  hintText:
                      'e.g. 12.90,77.50 12.90,77.70 13.10,77.70 13.10,77.50 12.90,77.50',
                  border: OutlineInputBorder(),
                ),
                validator: (v) => (v == null || v.trim().isEmpty)
                    ? 'Polygon coordinates required'
                    : null,
              ),
            ],
            const SizedBox(height: 20),

            Text('Validity Timeline', style: theme.textTheme.titleMedium),
            const SizedBox(height: 12),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Expiration Time'),
              subtitle: Text(
                _expiresAt.toLocal().toString().substring(0, 16),
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              trailing: OutlinedButton(
                onPressed: () async {
                  final now = DateTime.now();
                  final pickedDate = await showDatePicker(
                    context: context,
                    initialDate: _expiresAt,
                    firstDate: now,
                    lastDate: now.add(const Duration(days: 30)),
                  );
                  if (pickedDate != null && mounted) {
                    final pickedTime = await showTimePicker(
                      context: context,
                      initialTime: TimeOfDay.fromDateTime(_expiresAt),
                    );
                    if (pickedTime != null && mounted) {
                      setState(() {
                        _expiresAt = DateTime(
                          pickedDate.year,
                          pickedDate.month,
                          pickedDate.day,
                          pickedTime.hour,
                          pickedTime.minute,
                        );
                      });
                    }
                  }
                },
                child: const Text('Change'),
              ),
            ),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}

class _SubmissionConfirmationDialog extends StatelessWidget {
  const _SubmissionConfirmationDialog({
    required this.event,
    required this.severity,
    required this.urgency,
    required this.areaDesc,
    required this.expiresAt,
    required this.isQualifyingOverride,
  });

  final String event;
  final String severity;
  final String urgency;
  final String areaDesc;
  final DateTime expiresAt;
  final bool isQualifyingOverride;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return AlertDialog(
      title: const Text('Confirm SACHET Broadcast'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (isQualifyingOverride) ...[
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: isDark
                      ? Colors.red.shade900.withValues(alpha: 0.25)
                      : Colors.red.shade100,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: isDark ? Colors.red.shade800 : Colors.red.shade700,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.warning,
                      color: isDark ? Colors.red.shade300 : Colors.red.shade900,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'HIGH-PRIORITY DISASTER OVERRIDE: Alerts with this severity and urgency trigger an emergency override in GeoShield.',
                        style: TextStyle(
                          color: isDark ? Colors.red.shade100 : Colors.red.shade900,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
            ],
            Text('Event: $event', style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            Text('Severity: $severity · Urgency: $urgency'),
            const SizedBox(height: 4),
            Text('Area: ${areaDesc.isNotEmpty ? areaDesc : "Specified geometry"}'),
            const SizedBox(height: 4),
            Text('Expires: ${expiresAt.toLocal().toString().substring(0, 16)}'),
            const SizedBox(height: 12),
            const Text(
              'Once broadcasted, this alert will immediately be visible to emergency responders and affected tourists.',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text('Confirm Broadcast'),
        ),
      ],
    );
  }
}
