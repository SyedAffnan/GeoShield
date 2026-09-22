/// Model representing a structured recent safety news item.
class RecentSafetyEventModel {
  const RecentSafetyEventModel({
    required this.id,
    this.eventGroupId,
    required this.title,
    required this.description,
    required this.sourceName,
    required this.sourceUrl,
    this.imageUrl,
    required this.publishedAt,
    required this.retrievedAt,
    required this.category,
    required this.severity,
    required this.relevance,
    required this.areaName,
    required this.isVerifiedSource,
    required this.relatedSourcesCount,
  });

  final String id;
  final String? eventGroupId;
  final String title;
  final String description;
  final String sourceName;
  final String sourceUrl;
  final String? imageUrl;
  final DateTime publishedAt;
  final DateTime retrievedAt;
  final String category;
  final String severity;
  final String relevance;
  final String areaName;
  final bool isVerifiedSource;
  final int relatedSourcesCount;

  factory RecentSafetyEventModel.fromJson(Map<String, dynamic> json) {
    return RecentSafetyEventModel(
      id: json['eventId']?.toString() ?? '',
      eventGroupId: json['eventGroupId'] as String?,
      title: json['title'] as String? ?? '',
      description: json['description'] as String? ?? '',
      sourceName: json['sourceName'] as String? ?? 'News',
      sourceUrl: json['sourceUrl'] as String? ?? '',
      imageUrl: json['imageUrl'] as String?,
      publishedAt: json['publishedAt'] != null
          ? (DateTime.tryParse(json['publishedAt'].toString()) ?? DateTime.now())
          : DateTime.now(),
      retrievedAt: json['retrievedAt'] != null
          ? (DateTime.tryParse(json['retrievedAt'].toString()) ?? DateTime.now())
          : DateTime.now(),
      category: json['category'] as String? ?? 'GENERAL_SAFETY',
      severity: json['severity'] as String? ?? 'UNKNOWN',
      relevance: json['relevance'] as String? ?? 'MEDIUM',
      areaName: json['areaName'] as String? ?? '',
      isVerifiedSource: json['isVerifiedSource'] as bool? ?? false,
      relatedSourcesCount: (json['relatedSourcesCount'] as num?)?.toInt() ?? 1,
    );
  }
}

/// Response model for recent safety news queries.
class NewsResponseModel {
  const NewsResponseModel({
    required this.resolvedArea,
    required this.resolutionLevel,
    required this.retrievedAt,
    required this.cached,
    required this.providerAvailable,
    required this.eventsCount,
    required this.events,
  });

  final String resolvedArea;
  final String resolutionLevel;
  final DateTime retrievedAt;
  final bool cached;
  final bool providerAvailable;
  final int eventsCount;
  final List<RecentSafetyEventModel> events;

  factory NewsResponseModel.fromJson(Map<String, dynamic> json) {
    final rawEvents = json['events'] as List<dynamic>? ?? [];
    return NewsResponseModel(
      resolvedArea: json['resolvedArea'] as String? ?? 'India',
      resolutionLevel: json['resolutionLevel'] as String? ?? 'NATIONAL',
      retrievedAt: json['retrievedAt'] != null
          ? DateTime.parse(json['retrievedAt'] as String)
          : DateTime.now(),
      cached: json['cached'] as bool? ?? false,
      providerAvailable: json['providerAvailable'] as bool? ?? false,
      eventsCount: (json['eventsCount'] as num?)?.toInt() ?? 0,
      events: rawEvents
          .map((e) => RecentSafetyEventModel.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(growable: false),
    );
  }
}
