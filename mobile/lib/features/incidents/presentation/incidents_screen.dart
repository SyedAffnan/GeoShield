import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../risk/data/risk_repository.dart';

class IncidentsScreen extends ConsumerWidget {
  const IncidentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final incidents = ref.watch(incidentsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Your incident reports')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showReportSheet(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('Report incident'),
      ),
      body: incidents.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) =>
            _Retry(onPressed: () => ref.invalidate(incidentsProvider)),
        data: (items) => items.isEmpty
            ? const Center(child: Text('No incidents have been reported.'))
            : RefreshIndicator(
                onRefresh: () async => ref.invalidate(incidentsProvider),
                child: ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  itemBuilder: (_, index) => Card(
                    child: ListTile(
                      leading: const Icon(Icons.report_outlined),
                      title: Text(items[index].incidentType),
                      subtitle: Text(items[index].description),
                      trailing: Text(items[index].status),
                    ),
                  ),
                ),
              ),
      ),
    );
  }

  Future<void> _showReportSheet(BuildContext context, WidgetRef ref) async {
    final descriptionController = TextEditingController();
    final typeController = TextEditingController();
    final formKey = GlobalKey<FormState>();
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.fromLTRB(
            24, 24, 24, MediaQuery.viewInsetsOf(sheetContext).bottom + 24),
        child: Form(
          key: formKey,
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text('Report an incident',
                style: Theme.of(sheetContext).textTheme.titleLarge),
            const SizedBox(height: 16),
            TextFormField(
              controller: typeController,
              decoration: const InputDecoration(
                  labelText: 'Incident type', border: OutlineInputBorder()),
              validator: (value) => value == null || value.trim().isEmpty
                  ? 'Enter an incident type.'
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: descriptionController,
              minLines: 3,
              maxLines: 5,
              decoration: const InputDecoration(
                  labelText: 'Description', border: OutlineInputBorder()),
              validator: (value) => value == null || value.trim().isEmpty
                  ? 'Enter a description.'
                  : null,
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () async {
                if (!formKey.currentState!.validate()) return;
                try {
                  final location = CurrentLocation.fromJson(await ref
                      .read(apiClientProvider)
                      .getData('/api/v1/locations'));
                  await ref.read(incidentRepositoryProvider).report(
                        type: typeController.text.trim(),
                        description: descriptionController.text.trim(),
                        location: location,
                      );
                  ref.invalidate(incidentsProvider);
                  if (sheetContext.mounted) Navigator.pop(sheetContext);
                } catch (_) {
                  if (sheetContext.mounted) {
                    ScaffoldMessenger.of(sheetContext).showSnackBar(
                      const SnackBar(
                          content: Text(
                              'Unable to report the incident. Check your current location and try again.')),
                    );
                  }
                }
              },
              child: const Text('Submit report'),
            ),
          ]),
        ),
      ),
    );
    descriptionController.dispose();
    typeController.dispose();
  }
}

class _Retry extends StatelessWidget {
  const _Retry({required this.onPressed});
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Text('Unable to load incident reports.'),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: onPressed, child: const Text('Try again')),
        ]),
      );
}
