import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../data/admin_repository.dart';

final adminStatsProvider =
    FutureProvider.autoDispose<AdminStats>((ref) async {
  return ref.watch(adminRepositoryProvider).fetchStats();
});

class AdminDashboardScreen extends ConsumerWidget {
  const AdminDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statsAsync = ref.watch(adminStatsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Admin Console'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: () => ref.invalidate(adminStatsProvider),
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            tooltip: 'API configuration',
            onPressed: () => context.push('/settings'),
            icon: const Icon(Icons.settings_outlined),
          ),
          IconButton(
            tooltip: 'Sign out',
            onPressed: () async {
              await ref.read(authControllerProvider.notifier).logout();
              if (context.mounted) context.go('/login');
            },
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: statsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, size: 48, color: Colors.red),
                const SizedBox(height: 16),
                Text(
                  'Failed to load administration stats.\n$error',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: () => ref.invalidate(adminStatsProvider),
                  icon: const Icon(Icons.refresh),
                  label: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
        data: (stats) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(adminStatsProvider),
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _HeaderCard(stats: stats),
                const SizedBox(height: 20),
                Text(
                  'System Overview',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 12),
                GridView.count(
                  crossAxisCount: 2,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  crossAxisSpacing: 12,
                  mainAxisSpacing: 12,
                  childAspectRatio: 1.5,
                  children: [
                    _StatCard(
                      title: 'Total Users',
                      value: '${stats.totalUsers}',
                      icon: Icons.people_outline,
                      color: Colors.blue,
                    ),
                    _StatCard(
                      title: 'Tourists',
                      value: '${stats.touristCount}',
                      icon: Icons.person_pin_circle_outlined,
                      color: Colors.teal,
                    ),
                    _StatCard(
                      title: 'Responders',
                      value: '${stats.responderCount}',
                      icon: Icons.medical_services_outlined,
                      color: Colors.orange,
                    ),
                    _StatCard(
                      title: 'Admins',
                      value: '${stats.adminCount}',
                      icon: Icons.admin_panel_settings_outlined,
                      color: Colors.purple,
                    ),
                    _StatCard(
                      title: 'Active Incidents',
                      value: '${stats.activeIncidents}',
                      icon: Icons.warning_amber_outlined,
                      color: Colors.amber.shade800,
                    ),
                    _StatCard(
                      title: 'Resolved Incidents',
                      value: '${stats.resolvedIncidents}',
                      icon: Icons.check_circle_outline,
                      color: Colors.green,
                    ),
                    _StatCard(
                      title: 'Location Pings',
                      value: '${stats.totalLocationsRecorded}',
                      icon: Icons.location_on_outlined,
                      color: Colors.indigo,
                    ),
                    _StatCard(
                      title: 'Active SOS',
                      value: '${stats.activeSosAlerts}',
                      icon: Icons.sos,
                      color: Colors.red,
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                Text(
                  'Management Actions',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 12),
                _ActionTile(
                  icon: Icons.manage_accounts_outlined,
                  title: 'User Management',
                  subtitle: 'View, filter, activate, or deactivate accounts',
                  onTap: () => context.push('/admin/users'),
                ),
                const SizedBox(height: 8),
                _ActionTile(
                  icon: Icons.person_add_alt_1_outlined,
                  title: 'Provision Privileged User',
                  subtitle: 'Create a new Emergency Responder or Administrator',
                  onTap: () async {
                    await context.push('/admin/provision');
                    ref.invalidate(adminStatsProvider);
                  },
                ),
                const SizedBox(height: 8),
                _ActionTile(
                  icon: Icons.report_outlined,
                  title: 'Incident Oversight',
                  subtitle: 'System-wide tourist safety reports',
                  onTap: () => context.push('/admin/incidents'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _HeaderCard extends StatelessWidget {
  const _HeaderCard({required this.stats});
  final AdminStats stats;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.shield_outlined,
                  size: 32,
                  color: Theme.of(context).colorScheme.onPrimaryContainer,
                ),
                const SizedBox(width: 12),
                Text(
                  'GeoShield Administration',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: Theme.of(context).colorScheme.onPrimaryContainer,
                      ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Real-time administrative operations, user governance, and incident oversight.',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onPrimaryContainer,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({
    required this.title,
    required this.value,
    required this.icon,
    required this.color,
  });

  final String title;
  final String value;
  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    title,
                    style: Theme.of(context).textTheme.bodySmall,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Icon(icon, size: 20, color: color),
              ],
            ),
            Text(
              value,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: color,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ActionTile extends StatelessWidget {
  const _ActionTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        side: BorderSide(color: Theme.of(context).dividerColor),
        borderRadius: BorderRadius.circular(12),
      ),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor:
              Theme.of(context).colorScheme.primary.withValues(alpha: 0.1),
          child: Icon(icon, color: Theme.of(context).colorScheme.primary),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(subtitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
