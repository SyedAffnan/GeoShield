import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';

class SessionGate extends ConsumerWidget {
  const SessionGate({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apiConfiguration = ref.watch(apiConfigurationProvider);
    final session = ref.watch(authControllerProvider);
    if (apiConfiguration.isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (apiConfiguration.hasError) {
      return _message(context, 'Unable to restore the API configuration.');
    }
    return session.when(
      loading: () =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (_, __) => _message(
          context, 'Unable to restore your session. Please sign in again.'),
      data: (value) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (value == null) {
            context.go('/login');
          } else if (value.isAdmin) {
            context.go('/admin');
          } else if (value.isResponder) {
            context.go('/responder');
          } else {
            context.go('/dashboard');
          }
        });
        return const Scaffold(body: Center(child: CircularProgressIndicator()));
      },
    );
  }

  Widget _message(BuildContext context, String message) => Scaffold(
        body: Center(
            child: Padding(
                padding: const EdgeInsets.all(24), child: Text(message))),
      );
}
