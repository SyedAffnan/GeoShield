import '../../../core/network/api_client.dart';
import '../../risk/data/sachet_alert_model.dart';

/// Repository for NDMA SACHET CAP 1.2 disaster alert administrative operations.
class AdminSachetRepository {
  AdminSachetRepository(this._client);
  final GeoShieldApiClient _client;

  /// Fetches all active, unexpired, non-cancelled SACHET disaster alerts.
  Future<List<SachetAlertModel>> fetchActiveAlerts() async {
    final list = await _client.getListData('/api/v1/alerts/sachet/active');
    return list
        .map((item) => SachetAlertModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// Ingests a CAP 1.2 XML disaster broadcast payload.
  Future<SachetAlertModel> ingestAlertXml(String capXml) async {
    final data = await _client.postRaw(
      '/api/v1/alerts/sachet',
      data: capXml,
      contentType: 'application/xml',
    );
    return SachetAlertModel.fromJson(data);
  }

  /// Cancels an active SACHET alert using a standard CAP 1.2 Cancel broadcast
  /// submitted through the authoritative POST endpoint.
  Future<SachetAlertModel> cancelAlert(SachetAlertModel alert) async {
    final cancelXml = buildCancelCapXml(alert);
    return ingestAlertXml(cancelXml);
  }

  /// Constructs a validated CAP 1.2 XML document string from structured fields.
  static String buildCapXml({
    required String identifier,
    required String sender,
    required DateTime sentAt,
    required String status,
    required String msgType,
    String? references,
    required String category,
    required String event,
    required String urgency,
    required String severity,
    required String certainty,
    DateTime? effectiveAt,
    required DateTime expiresAt,
    String? headline,
    String? description,
    String? instruction,
    String? areaDesc,
    String? circle,
    String? polygon,
  }) {
    final buffer = StringBuffer();
    buffer.writeln('<?xml version="1.0" encoding="UTF-8"?>');
    buffer.writeln('<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">');
    buffer.writeln('  <identifier>${_xmlEscape(identifier)}</identifier>');
    buffer.writeln('  <sender>${_xmlEscape(sender)}</sender>');
    buffer.writeln('  <sent>${sentAt.toUtc().toIso8601String()}</sent>');
    buffer.writeln('  <status>${_xmlEscape(status)}</status>');
    buffer.writeln('  <msgType>${_xmlEscape(msgType)}</msgType>');
    if (references != null && references.trim().isNotEmpty) {
      buffer.writeln('  <references>${_xmlEscape(references.trim())}</references>');
    }
    buffer.writeln('  <info>');
    buffer.writeln('    <category>${_xmlEscape(category)}</category>');
    buffer.writeln('    <event>${_xmlEscape(event)}</event>');
    buffer.writeln('    <urgency>${_xmlEscape(urgency)}</urgency>');
    buffer.writeln('    <severity>${_xmlEscape(severity)}</severity>');
    buffer.writeln('    <certainty>${_xmlEscape(certainty)}</certainty>');
    if (effectiveAt != null) {
      buffer.writeln('    <effective>${effectiveAt.toUtc().toIso8601String()}</effective>');
    }
    buffer.writeln('    <expires>${expiresAt.toUtc().toIso8601String()}</expires>');
    if (headline != null && headline.trim().isNotEmpty) {
      buffer.writeln('    <headline>${_xmlEscape(headline.trim())}</headline>');
    }
    if (description != null && description.trim().isNotEmpty) {
      buffer.writeln('    <description>${_xmlEscape(description.trim())}</description>');
    }
    if (instruction != null && instruction.trim().isNotEmpty) {
      buffer.writeln('    <instruction>${_xmlEscape(instruction.trim())}</instruction>');
    }
    buffer.writeln('    <area>');
    if (areaDesc != null && areaDesc.trim().isNotEmpty) {
      buffer.writeln('      <areaDesc>${_xmlEscape(areaDesc.trim())}</areaDesc>');
    }
    if (circle != null && circle.trim().isNotEmpty) {
      buffer.writeln('      <circle>${circle.trim()}</circle>');
    }
    if (polygon != null && polygon.trim().isNotEmpty) {
      buffer.writeln('      <polygon>${polygon.trim()}</polygon>');
    }
    buffer.writeln('    </area>');
    buffer.writeln('  </info>');
    buffer.writeln('</alert>');
    return buffer.toString();
  }

  /// Generates a compliant CAP 1.2 cancellation XML for the referenced alert.
  static String buildCancelCapXml(SachetAlertModel alert) {
    final now = DateTime.now();
    final cancelId = 'CANCEL-${alert.identifier}-${now.millisecondsSinceEpoch}';
    final refString = '${alert.sender},${alert.identifier},${alert.sentAt.toUtc().toIso8601String()}';

    return buildCapXml(
      identifier: cancelId,
      sender: alert.sender,
      sentAt: now,
      status: alert.status ?? 'Actual',
      msgType: 'Cancel',
      references: refString,
      category: alert.category.isNotEmpty ? alert.category : 'Safety',
      event: alert.event,
      urgency: 'Past',
      severity: 'Minor',
      certainty: 'Observed',
      expiresAt: now.add(const Duration(minutes: 5)),
      headline: 'Cancellation of ${alert.identifier}',
      description: 'Administrative cancellation of broadcast ${alert.identifier}',
      areaDesc: alert.areaDesc ?? 'All previously affected areas',
      circle: '0.0,0.0 0.1',
    );
  }

  static String _xmlEscape(String input) {
    return input
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&apos;');
  }
}
