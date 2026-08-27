import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../data/admin_repository.dart';

final selectedRoleFilterProvider = StateProvider.autoDispose<String?>((ref) => null);

final adminUsersProvider =
    FutureProvider.autoDispose<List<AdminUser>>((ref) async {
  final role = ref.watch(selectedRoleFilterProvider);
  return ref.watch(adminRepositoryProvider).fetchUsers(role: role);
});

class AdminUsersScreen extends ConsumerWidget {
  const AdminUsersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selectedRole = ref.watch(selectedRoleFilterProvider);
    final usersAsync = ref.watch(adminUsersProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('User Management'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: () => ref.invalidate(adminUsersProvider),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: Column(
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                _FilterChip(
                  label: 'All Users',
                  selected: selectedRole == null,
                  onSelected: () =>
                      ref.read(selectedRoleFilterProvider.notifier).state = null,
                ),
                const SizedBox(width: 8),
                _FilterChip(
                  label: 'Tourists',
                  selected: selectedRole == 'TOURIST',
                  onSelected: () => ref
                      .read(selectedRoleFilterProvider.notifier)
                      .state = 'TOURIST',
                ),
                const SizedBox(width: 8),
                _FilterChip(
                  label: 'Responders',
                  selected: selectedRole == 'RESPONDER',
                  onSelected: () => ref
                      .read(selectedRoleFilterProvider.notifier)
                      .state = 'RESPONDER',
                ),
                const SizedBox(width: 8),
                _FilterChip(
                  label: 'Admins',
                  selected: selectedRole == 'ADMIN',
                  onSelected: () => ref
                      .read(selectedRoleFilterProvider.notifier)
                      .state = 'ADMIN',
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: usersAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, _) => Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.error_outline, size: 48, color: Colors.red),
                      const SizedBox(height: 16),
                      Text('Error loading users:\n$error',
                          textAlign: TextAlign.center),
                      const SizedBox(height: 16),
                      FilledButton(
                        onPressed: () => ref.invalidate(adminUsersProvider),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
              ),
              data: (users) {
                if (users.isEmpty) {
                  return const Center(
                    child: Text('No users found for this filter.'),
                  );
                }
                return ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: users.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final user = users[index];
                    return _UserCard(
                      user: user,
                      onToggleStatus: (active) async {
                        try {
                          await ref
                              .read(adminRepositoryProvider)
                              .updateUserStatus(
                                userId: user.userId,
                                active: active,
                              );
                          ref.invalidate(adminUsersProvider);
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  active
                                      ? 'User ${user.username} activated.'
                                      : 'User ${user.username} deactivated.',
                                ),
                              ),
                            );
                          }
                        } catch (e) {
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text('Failed to update status: $e'),
                              ),
                            );
                          }
                        }
                      },
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onSelected,
  });

  final String label;
  final bool selected;
  final VoidCallback onSelected;

  @override
  Widget build(BuildContext context) {
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => onSelected(),
    );
  }
}

class _UserCard extends StatelessWidget {
  const _UserCard({
    required this.user,
    required this.onToggleStatus,
  });

  final AdminUser user;
  final ValueChanged<bool> onToggleStatus;

  Color _roleColor(String role) {
    switch (role) {
      case 'ADMIN':
        return Colors.purple;
      case 'RESPONDER':
        return Colors.orange;
      case 'TOURIST':
      default:
        return Colors.blue;
    }
  }

  @override
  Widget build(BuildContext context) {
    final roleColor = _roleColor(user.role);

    return Card(
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  backgroundColor: roleColor.withValues(alpha: 0.2),
                  child: Text(
                    user.fullName.isNotEmpty
                        ? user.fullName[0].toUpperCase()
                        : user.username[0].toUpperCase(),
                    style: TextStyle(
                      color: roleColor,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        user.fullName.isNotEmpty ? user.fullName : user.username,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      Text(
                        '@${user.username}',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: roleColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: roleColor),
                  ),
                  child: Text(
                    user.role,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: roleColor,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                const Icon(Icons.email_outlined, size: 16, color: Colors.grey),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    user.email,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                const Icon(Icons.phone_outlined, size: 16, color: Colors.grey),
                const SizedBox(width: 6),
                Text(
                  user.phoneNumber,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
            const Divider(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      user.active ? Icons.check_circle : Icons.cancel,
                      size: 16,
                      color: user.active ? Colors.green : Colors.red,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      user.active ? 'Active Account' : 'Deactivated',
                      style: TextStyle(
                        fontSize: 13,
                        color: user.active ? Colors.green : Colors.red,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
                OutlinedButton.icon(
                  onPressed: () {
                    final newStatus = !user.active;
                    showDialog(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: Text(newStatus
                            ? 'Activate Account'
                            : 'Deactivate Account'),
                        content: Text(
                          'Are you sure you want to ${newStatus ? 'activate' : 'deactivate'} user @${user.username}?',
                        ),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(ctx),
                            child: const Text('Cancel'),
                          ),
                          FilledButton(
                            onPressed: () {
                              Navigator.pop(ctx);
                              onToggleStatus(newStatus);
                            },
                            child: Text(newStatus ? 'Activate' : 'Deactivate'),
                          ),
                        ],
                      ),
                    );
                  },
                  icon: Icon(
                    user.active ? Icons.block : Icons.check,
                    size: 16,
                  ),
                  label: Text(user.active ? 'Deactivate' : 'Activate'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: user.active ? Colors.red : Colors.green,
                    side: BorderSide(
                      color: user.active ? Colors.red : Colors.green,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
