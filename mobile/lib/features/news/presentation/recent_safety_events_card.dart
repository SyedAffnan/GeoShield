import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/recent_safety_event_model.dart';
import 'news_state.dart';

/// Card widget displaying recent location-based safety events and news intelligence
/// on the tourist dashboard.
class RecentSafetyEventsCard extends ConsumerWidget {
  const RecentSafetyEventsCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final newsAsync = ref.watch(recentNewsProvider);
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: colorScheme.outlineVariant.withValues(alpha: 0.5),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: newsAsync.when(
          loading: () => Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildHeader(context, null),
              const SizedBox(height: 16),
              const Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 12),
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            ],
          ),
          error: (error, _) => Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildHeader(context, null),
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(Icons.info_outline, size: 16, color: colorScheme.error),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Unable to load local safety news at this time.',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: colorScheme.error,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.refresh, size: 18),
                    onPressed: () => ref.invalidate(recentNewsProvider),
                  ),
                ],
              ),
            ],
          ),
          data: (newsResponse) => _buildContent(context, ref, newsResponse),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String? resolvedArea) {
    final theme = Theme.of(context);
    return Row(
      children: [
        const Icon(Icons.newspaper_rounded, size: 20, color: Colors.blueGrey),
        const SizedBox(width: 8),
        Text(
          'Recent Safety News',
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.bold,
          ),
        ),
        const Spacer(),
        if (resolvedArea != null && resolvedArea.isNotEmpty)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: Colors.blueGrey.shade50,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.blueGrey.shade200),
            ),
            child: Text(
              resolvedArea,
              style: theme.textTheme.labelSmall?.copyWith(
                color: Colors.blueGrey.shade800,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildContent(
      BuildContext context, WidgetRef ref, NewsResponseModel response) {
    final theme = Theme.of(context);

    if (!response.providerAvailable) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildHeader(context, response.resolvedArea),
          const SizedBox(height: 8),
          Text(
            'Regional news feed currently unavailable.',
            style: theme.textTheme.bodySmall?.copyWith(color: Colors.black54),
          ),
        ],
      );
    }

    if (response.events.isEmpty) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildHeader(context, response.resolvedArea),
          const SizedBox(height: 8),
          Row(
            children: [
              const Icon(Icons.check_circle_outline,
                  size: 16, color: Colors.green),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'No recent safety incidents reported in ${response.resolvedArea} in the past 72 hours.',
                  style: theme.textTheme.bodySmall?.copyWith(color: Colors.green.shade800),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Reported in ${response.resolvedArea} (Regional News Report)',
            style: theme.textTheme.labelSmall?.copyWith(
              color: Colors.black45,
              fontStyle: FontStyle.italic,
            ),
          ),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildHeader(context, response.resolvedArea),
        const SizedBox(height: 4),
        Text(
          'Reported in ${response.resolvedArea} (Regional News Report)',
          style: theme.textTheme.labelSmall?.copyWith(
            color: Colors.black45,
            fontStyle: FontStyle.italic,
          ),
        ),
        const SizedBox(height: 12),
        ListView.separated(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: response.events.length,
          separatorBuilder: (_, __) => const Divider(height: 16),
          itemBuilder: (context, index) {
            final event = response.events[index];
            return _buildEventTile(context, event);
          },
        ),
      ],
    );
  }

  Widget _buildEventTile(BuildContext context, RecentSafetyEventModel event) {
    final theme = Theme.of(context);

    final categoryIcon = switch (event.category) {
      'NATURAL_DISASTER' => Icons.warning_amber_rounded,
      'TRAFFIC_AND_TRANSIT' => Icons.directions_car_rounded,
      'FIRE_AND_EXPLOSION' => Icons.local_fire_department_rounded,
      'INFRASTRUCTURE_HAZARD' => Icons.construction_rounded,
      'CIVIL_DISTURBANCE' => Icons.groups_rounded,
      'CRIME_AND_VIOLENCE' => Icons.shield_outlined,
      _ => Icons.info_outline_rounded,
    };

    final severityColor = switch (event.severity) {
      'CRITICAL' => Colors.red.shade700,
      'HIGH' => Colors.orange.shade800,
      'MODERATE' => Colors.amber.shade900,
      _ => Colors.blueGrey.shade700,
    };

    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: () => _showEventDetailsDialog(context, event),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: severityColor.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(categoryIcon, size: 20, color: severityColor),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    event.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Text(
                        event.sourceName,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: Colors.blueGrey.shade800,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        '•  ${_formatRelativeTime(event.publishedAt)}',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: Colors.black54,
                        ),
                      ),
                      if (event.relatedSourcesCount > 1) ...[
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                          decoration: BoxDecoration(
                            color: Colors.grey.shade200,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            '+${event.relatedSourcesCount - 1} more',
                            style: theme.textTheme.labelSmall?.copyWith(fontSize: 10),
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: severityColor.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                event.severity,
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  color: severityColor,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showEventDetailsDialog(BuildContext context, RecentSafetyEventModel event) {
    showDialog(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        title: Row(
          children: [
            const Icon(Icons.newspaper_rounded, size: 22, color: Colors.blueGrey),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                event.category.replaceAll('_', ' '),
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                event.title,
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
              ),
              const SizedBox(height: 8),
              if (event.description.isNotEmpty) ...[
                Text(event.description, style: const TextStyle(fontSize: 13, height: 1.4)),
                const SizedBox(height: 12),
              ],
              Text(
                'Source: ${event.sourceName} • ${_formatRelativeTime(event.publishedAt)}',
                style: const TextStyle(fontSize: 11, color: Colors.black54, fontWeight: FontWeight.w500),
              ),
              const SizedBox(height: 4),
              if (event.sourceUrl.isNotEmpty)
                GestureDetector(
                  onTap: () {
                    Clipboard.setData(ClipboardData(text: event.sourceUrl));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Article URL copied to clipboard')),
                    );
                  },
                  child: Text(
                    event.sourceUrl,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 11, color: Colors.blue, decoration: TextDecoration.underline),
                  ),
                ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.amber.shade50,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: Colors.amber.shade200),
                ),
                child: const Text(
                  'Third-party regional news report. Not an internally verified GeoShield incident.',
                  style: TextStyle(fontSize: 10, color: Colors.black87, fontStyle: FontStyle.italic),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogCtx).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  String _formatRelativeTime(DateTime time) {
    final diff = DateTime.now().difference(time);
    if (diff.inMinutes < 60) {
      return '${diff.inMinutes}m ago';
    }
    if (diff.inHours < 24) {
      return '${diff.inHours}h ago';
    }
    return '${diff.inDays}d ago';
  }
}
