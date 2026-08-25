import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/session_gate.dart';
import '../../features/incidents/presentation/incidents_screen.dart';
import '../../features/risk/presentation/risk_dashboard_screen.dart';

final GoRouter appRouter = GoRouter(
  initialLocation: '/',
  routes: <RouteBase>[
    GoRoute(path: '/', builder: (_, __) => const SessionGate()),
    GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
    GoRoute(
        path: '/dashboard', builder: (_, __) => const RiskDashboardScreen()),
    GoRoute(path: '/incidents', builder: (_, __) => const IncidentsScreen()),
  ],
);
