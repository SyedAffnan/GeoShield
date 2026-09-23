import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/network/api_client.dart';
import 'package:geoshield_mobile/core/storage/secure_session_storage.dart';
import 'package:geoshield_mobile/features/admin/data/admin_sachet_repository.dart';
import 'package:geoshield_mobile/features/risk/data/sachet_alert_model.dart';

void main() {
  group('AdminSachetRepository Tests', () {
    test('fetchActiveAlerts calls GET /api/v1/alerts/sachet/active and parses alerts', () async {
      final interceptor = _CapturingInterceptor([
        {
          'id': '11111111-1111-1111-1111-111111111111',
          'identifier': 'NDMA-2026-CYC-0042',
          'sender': 'imd_alert@sachet.ndma.gov.in',
          'sentAt': '2026-09-23T10:00:00Z',
          'status': 'Actual',
          'msgType': 'Alert',
          'category': 'Met',
          'event': 'Severe Cyclonic Storm',
          'urgency': 'Immediate',
          'severity': 'Extreme',
          'certainty': 'Observed',
          'effectiveAt': '2026-09-23T10:00:00Z',
          'expiresAt': '2026-09-24T18:00:00Z',
          'headline': 'Severe Cyclone Approaching',
          'description': 'High wind speeds and storm surges expected.',
          'instruction': 'Evacuate coastal zones.',
          'areaDesc': 'Puri and Jagatsinghpur',
          'isSynthetic': false,
        }
      ]);
      final repo = AdminSachetRepository(_client(interceptor));

      final alerts = await repo.fetchActiveAlerts();

      expect(interceptor.method, 'GET');
      expect(interceptor.path, '/api/v1/alerts/sachet/active');
      expect(alerts.length, 1);
      final a = alerts.first;
      expect(a.identifier, 'NDMA-2026-CYC-0042');
      expect(a.sender, 'imd_alert@sachet.ndma.gov.in');
      expect(a.severity, 'Extreme');
      expect(a.urgency, 'Immediate');
      expect(a.event, 'Severe Cyclonic Storm');
      expect(a.isSynthetic, isFalse);
    });

    test('ingestAlertXml sends XML string via POST /api/v1/alerts/sachet with application/xml', () async {
      final interceptor = _CapturingInterceptor({
        'id': '22222222-2222-2222-2222-222222222222',
        'identifier': 'TEST-CAP-001',
        'sender': 'admin@test.org',
        'sentAt': '2026-09-23T12:00:00Z',
        'status': 'Test',
        'msgType': 'Alert',
        'category': 'Safety',
        'event': 'Flash Flood Drill',
        'urgency': 'Expected',
        'severity': 'Severe',
        'certainty': 'Likely',
        'effectiveAt': '2026-09-23T12:00:00Z',
        'expiresAt': '2026-09-24T12:00:00Z',
        'headline': 'Drill Headline',
        'areaDesc': 'Test Area',
        'isSynthetic': true,
      });
      final repo = AdminSachetRepository(_client(interceptor));

      const testXml = '<alert><identifier>TEST-CAP-001</identifier></alert>';
      final result = await repo.ingestAlertXml(testXml);

      expect(interceptor.method, 'POST');
      expect(interceptor.path, '/api/v1/alerts/sachet');
      expect(interceptor.headers?['Content-Type'], 'application/xml');
      expect(interceptor.body, testXml);
      expect(result.identifier, 'TEST-CAP-001');
      expect(result.isSynthetic, isTrue);
    });

    test('buildCapXml generates valid CAP 1.2 XML with proper escaping and structure', () {
      final xml = AdminSachetRepository.buildCapXml(
        identifier: 'NDMA-TEST & 01',
        sender: 'sender <alert>@ndma.gov.in',
        sentAt: DateTime.parse('2026-09-23T10:00:00Z'),
        status: 'Actual',
        msgType: 'Alert',
        category: 'Met',
        event: 'Severe Storm',
        urgency: 'Immediate',
        severity: 'Extreme',
        certainty: 'Observed',
        expiresAt: DateTime.parse('2026-09-24T10:00:00Z'),
        headline: 'Storm Warning "High"',
        instruction: "Take shelter & don't go outside",
        areaDesc: 'District A & B',
        circle: '11.0168,76.9558 15.0',
      );

      expect(xml, contains('<?xml version="1.0" encoding="UTF-8"?>'));
      expect(xml, contains('<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">'));
      expect(xml, contains('<identifier>NDMA-TEST &amp; 01</identifier>'));
      expect(xml, contains('<sender>sender &lt;alert&gt;@ndma.gov.in</sender>'));
      expect(xml, contains('<status>Actual</status>'));
      expect(xml, contains('<msgType>Alert</msgType>'));
      expect(xml, contains('<category>Met</category>'));
      expect(xml, contains('<event>Severe Storm</event>'));
      expect(xml, contains('<urgency>Immediate</urgency>'));
      expect(xml, contains('<severity>Extreme</severity>'));
      expect(xml, contains('<certainty>Observed</certainty>'));
      expect(xml, contains('<headline>Storm Warning &quot;High&quot;</headline>'));
      expect(xml, contains('<instruction>Take shelter &amp; don&apos;t go outside</instruction>'));
      expect(xml, contains('<areaDesc>District A &amp; B</areaDesc>'));
      expect(xml, contains('<circle>11.0168,76.9558 15.0</circle>'));
    });

    test('buildCancelCapXml generates valid CAP Cancel broadcast referencing the target alert', () {
      final alert = SachetAlertModel(
        id: '123',
        identifier: 'NDMA-TARGET-001',
        sender: 'target_sender@ndma.gov.in',
        sentAt: DateTime.parse('2026-09-23T08:00:00Z'),
        category: 'Met',
        event: 'Cyclone',
        urgency: 'Immediate',
        severity: 'Extreme',
        certainty: 'Observed',
        effectiveAt: DateTime.parse('2026-09-23T08:00:00Z'),
        expiresAt: DateTime.parse('2026-09-24T08:00:00Z'),
        areaDesc: 'Coast Area',
        isSynthetic: false,
        status: 'Actual',
        msgType: 'Alert',
      );

      final cancelXml = AdminSachetRepository.buildCancelCapXml(alert);

      expect(cancelXml, contains('<msgType>Cancel</msgType>'));
      expect(cancelXml, contains('<sender>target_sender@ndma.gov.in</sender>'));
      expect(cancelXml, contains('<references>target_sender@ndma.gov.in,NDMA-TARGET-001,2026-09-23T08:00:00.000Z</references>'));
      expect(cancelXml, contains('<urgency>Past</urgency>'));
      expect(cancelXml, contains('<severity>Minor</severity>'));
      expect(cancelXml, contains('<certainty>Observed</certainty>'));
      expect(cancelXml, contains('<circle>0.0,0.0 0.1</circle>'));
    });
  });
}

GeoShieldApiClient _client(_CapturingInterceptor interceptor) {
  final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080'));
  dio.interceptors.add(interceptor);
  return GeoShieldApiClient(_NoSessionStorage(), dio: dio);
}

class _CapturingInterceptor extends Interceptor {
  _CapturingInterceptor(this._responseData);
  final dynamic _responseData;

  String? method;
  String? path;
  dynamic body;
  Map<String, dynamic>? headers;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    method = options.method;
    path = options.path;
    body = options.data;
    headers = options.headers;
    handler.resolve(Response<dynamic>(
      requestOptions: options,
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': _responseData,
      },
    ));
  }
}

class _NoSessionStorage implements SecureSessionStorage {
  @override
  Future<String?> readAccessToken() async => null;
  @override
  Future<String?> readRole() async => null;
  @override
  Future<void> save({required String accessToken, required String role}) async {}
  @override
  Future<String?> readBackendBaseUrl() async => null;
  @override
  Future<void> saveBackendBaseUrl(String baseUrl) async {}
  @override
  Future<void> clearBackendBaseUrl() async {}
  @override
  Future<void> clear() async {}
}
