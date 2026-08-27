import 'package:go_router/go_router.dart';

import '../../features/admin/presentation/admin_dashboard_screen.dart';
import '../../features/admin/presentation/admin_incidents_screen.dart';
import '../../features/admin/presentation/admin_provision_user_screen.dart';
import '../../features/admin/presentation/admin_users_screen.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/register_screen.dart';
import '../../features/auth/presentation/session_gate.dart';
import '../../features/incidents/presentation/incidents_screen.dart';
import '../../features/responder/presentation/responder_dashboard_screen.dart';
import '../../features/responder/presentation/responder_incident_detail_screen.dart';
import '../../features/risk/presentation/risk_dashboard_screen.dart';
import '../../features/settings/presentation/api_configuration_screen.dart';
import '../../features/sos/data/sos_repository.dart';
import '../../features/sos/presentation/sos_status_screen.dart';

final GoRouter appRouter = GoRouter(
  initialLocation: '/',
  routes: <RouteBase>[
    GoRoute(path: '/', builder: (_, __) => const SessionGate()),
    GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
    GoRoute(path: '/register', builder: (_, __) => const RegisterScreen()),
    GoRoute(
        path: '/dashboard', builder: (_, __) => const RiskDashboardScreen()),
    GoRoute(path: '/incidents', builder: (_, __) => const IncidentsScreen()),
    GoRoute(
      path: '/sos',
      builder: (_, state) => SosStatusScreen(
        initialAlert: state.extra! as SosAlert,
      ),
    ),
    GoRoute(
        path: '/admin', builder: (_, __) => const AdminDashboardScreen()),
    GoRoute(
        path: '/admin/users', builder: (_, __) => const AdminUsersScreen()),
    GoRoute(
        path: '/admin/provision',
        builder: (_, __) => const AdminProvisionUserScreen()),
    GoRoute(
        path: '/admin/incidents',
        builder: (_, __) => const AdminIncidentsScreen()),
    GoRoute(
        path: '/responder',
        builder: (_, __) => const ResponderDashboardScreen()),
    GoRoute(
      path: '/responder/incident/:id',
      builder: (_, state) => ResponderIncidentDetailScreen(
        incidentId: state.pathParameters['id']!,
      ),
    ),
    GoRoute(
        path: '/settings', builder: (_, __) => const ApiConfigurationScreen()),
  ],
);
