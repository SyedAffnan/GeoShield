/// Model representing an NDMA SACHET CAP 1.2 disaster alert broadcast.
class SachetAlertModel {
  const SachetAlertModel({
    required this.id,
    required this.identifier,
    required this.sender,
    required this.sentAt,
    required this.category,
    required this.event,
    required this.urgency,
    required this.severity,
    required this.certainty,
    required this.effectiveAt,
    required this.expiresAt,
    this.headline,
    this.description,
    this.instruction,
    this.areaDesc,
    required this.isSynthetic,
    this.status,
    this.msgType,
  });

  final String id;
  final String identifier;
  final String sender;
  final DateTime sentAt;
  final String category;
  final String event;
  final String urgency;
  final String severity;
  final String certainty;
  final DateTime effectiveAt;
  final DateTime expiresAt;
  final String? headline;
  final String? description;
  final String? instruction;
  final String? areaDesc;
  final bool isSynthetic;
  final String? status;
  final String? msgType;

  factory SachetAlertModel.fromJson(Map<String, dynamic> json) {
    return SachetAlertModel(
      id: json['id']?.toString() ?? '',
      identifier: json['identifier'] as String? ?? '',
      sender: json['sender'] as String? ?? '',
      sentAt: json['sentAt'] != null
          ? DateTime.parse(json['sentAt'] as String)
          : DateTime.now(),
      category: json['category'] as String? ?? '',
      event: json['event'] as String? ?? '',
      urgency: json['urgency'] as String? ?? '',
      severity: json['severity'] as String? ?? '',
      certainty: json['certainty'] as String? ?? '',
      effectiveAt: json['effectiveAt'] != null
          ? DateTime.parse(json['effectiveAt'] as String)
          : DateTime.now(),
      expiresAt: json['expiresAt'] != null
          ? DateTime.parse(json['expiresAt'] as String)
          : DateTime.now(),
      headline: json['headline'] as String?,
      description: json['description'] as String?,
      instruction: json['instruction'] as String?,
      areaDesc: json['areaDesc'] as String?,
      isSynthetic: json['isSynthetic'] as bool? ?? false,
      status: json['status'] as String?,
      msgType: json['msgType'] as String?,
    );
  }
}
