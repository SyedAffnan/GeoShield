import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/network/api_client.dart';

class ApiConfigurationScreen extends ConsumerStatefulWidget {
  const ApiConfigurationScreen({super.key});

  @override
  ConsumerState<ApiConfigurationScreen> createState() =>
      _ApiConfigurationScreenState();
}

class _ApiConfigurationScreenState
    extends ConsumerState<ApiConfigurationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _urlController = TextEditingController();
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _save(String value) async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref
          .read(apiConfigurationProvider.notifier)
          .saveCustomBaseUrl(value);
      if (mounted) Navigator.pop(context);
    } on FormatException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) {
        setState(
            () => _error = 'Unable to save the backend URL. Please try again.');
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _useDefault() async {
    setState(() => _saving = true);
    await ref.read(apiConfigurationProvider.notifier).useDefault();
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final configuration = ref.watch(apiConfigurationProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('API configuration')),
      body: configuration.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text('Unable to load the API configuration.'),
          ),
        ),
        data: (value) => Padding(
          padding: const EdgeInsets.all(24),
          child: Builder(builder: (context) {
            if (_urlController.text.isEmpty) {
              _urlController.text =
                  value.customBaseUrl ?? value.effectiveBaseUrl;
            }
            return Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Backend server URL',
                      style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 8),
                  const Text(
                    'Enter only the server origin. API paths are already managed by the app.',
                  ),
                  const SizedBox(height: 20),
                  TextFormField(
                    controller: _urlController,
                    keyboardType: TextInputType.url,
                    autocorrect: false,
                    enableSuggestions: false,
                    decoration: const InputDecoration(
                      labelText: 'Backend URL',
                      hintText: 'http://192.168.0.13:8080',
                      border: OutlineInputBorder(),
                    ),
                    validator: (input) {
                      try {
                        GeoShieldApiClient.normalizeBaseUrl(input ?? '');
                        return null;
                      } on FormatException catch (error) {
                        return error.message;
                      }
                    },
                    onFieldSubmitted: _saving ? null : _save,
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Text(_error!,
                        style: TextStyle(
                            color: Theme.of(context).colorScheme.error)),
                  ],
                  const SizedBox(height: 20),
                  FilledButton(
                    onPressed:
                        _saving ? null : () => _save(_urlController.text),
                    child: const Text('Save backend URL'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton(
                    onPressed: _saving || value.customBaseUrl == null
                        ? null
                        : _useDefault,
                    child: const Text('Use default / dart-define URL'),
                  ),
                  const SizedBox(height: 20),
                  Text('Active URL: ${value.effectiveBaseUrl}'),
                ],
              ),
            );
          }),
        ),
      ),
    );
  }
}
