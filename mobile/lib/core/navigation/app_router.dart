import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';

/// Route guards and role-specific routes are scaffolded without screen implementations.
final GoRouter appRouter = GoRouter(
  initialLocation: '/login',
  routes: <RouteBase>[
    GoRoute(path: '/login', builder: (_, __) => const SizedBox.shrink()),
    GoRoute(path: '/responder', builder: (_, __) => const SizedBox.shrink()),
    GoRoute(path: '/admin', builder: (_, __) => const SizedBox.shrink()),
  ],
  // TODO: implement approved authentication and role guard using the auth-state provider.
);
