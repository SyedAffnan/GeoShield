import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geoshield_mobile/core/geofencing/models/geofence_zone.dart';
import 'package:geoshield_mobile/core/geofencing/services/geofence_notification_service.dart';
import 'package:geoshield_mobile/core/geofencing/services/geofencing_engine.dart';

class FakeFlutterLocalNotificationsPlugin implements FlutterLocalNotificationsPlugin {
  int initializeCallCount = 0;
  int showCallCount = 0;
  int? lastId;
  String? lastTitle;
  String? lastBody;
  NotificationDetails? lastNotificationDetails;

  @override
  Future<bool?> initialize({
    required InitializationSettings settings,
    void Function(NotificationResponse)? onDidReceiveNotificationResponse,
    void Function(NotificationResponse)? onDidReceiveBackgroundNotificationResponse,
  }) async {
    initializeCallCount++;
    return true;
  }

  @override
  Future<void> show({
    required int id,
    String? title,
    String? body,
    NotificationDetails? notificationDetails,
    String? payload,
  }) async {
    showCallCount++;
    lastId = id;
    lastTitle = title;
    lastBody = body;
    lastNotificationDetails = notificationDetails;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => null;
}

void main() {
  group('GeofenceNotificationService - Content Building & Formatting', () {
    test('CORE notification text is factual and exact', () {
      final event = GeofenceTransitionEvent(
        hazardId: 'HZ-001',
        hazardName: 'Real Hazard',
        previousZone: GeofenceZone.preWarning,
        nextZone: GeofenceZone.core,
        distanceMeters: 450.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: false,
        evaluatedAt: DateTime.now().toUtc(),
      );

      final (title, body) = GeofenceNotificationService.buildNotificationContent(event);
      expect(title, equals('Safety Alert'));
      expect(body, equals('Safety alert: you are inside the designated hazard zone.'));
    });

    test('PRE_WARNING notification text is factual and exact', () {
      final event = GeofenceTransitionEvent(
        hazardId: 'HZ-001',
        hazardName: 'Real Hazard',
        previousZone: GeofenceZone.outside,
        nextZone: GeofenceZone.preWarning,
        distanceMeters: 950.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: false,
        evaluatedAt: DateTime.now().toUtc(),
      );

      final (title, body) = GeofenceNotificationService.buildNotificationContent(event);
      expect(title, equals('Safety Warning'));
      expect(body, equals('Safety warning: approaching a designated hazard zone.'));
    });

    test('Synthetic test fixture visibly includes [TEST FIXTURE] prefix in title', () {
      final event = GeofenceTransitionEvent(
        hazardId: 'TEST-HZ-001',
        hazardName: 'Synthetic Fixture',
        previousZone: GeofenceZone.outside,
        nextZone: GeofenceZone.preWarning,
        distanceMeters: 900.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: true,
        evaluatedAt: DateTime.now().toUtc(),
      );

      final (title, body) = GeofenceNotificationService.buildNotificationContent(event);
      expect(title, equals('[TEST FIXTURE] Safety Warning'));
      expect(body, equals('Safety warning: approaching a designated hazard zone.'));

      final coreEvent = GeofenceTransitionEvent(
        hazardId: 'TEST-HZ-001',
        hazardName: 'Synthetic Fixture',
        previousZone: GeofenceZone.preWarning,
        nextZone: GeofenceZone.core,
        distanceMeters: 400.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: true,
        evaluatedAt: DateTime.now().toUtc(),
      );

      final (coreTitle, coreBody) = GeofenceNotificationService.buildNotificationContent(coreEvent);
      expect(coreTitle, equals('[TEST FIXTURE] Safety Alert'));
      expect(coreBody, equals('Safety alert: you are inside the designated hazard zone.'));
    });

    test('OUTSIDE de-escalation returns empty content without raising acute alert', () {
      final event = GeofenceTransitionEvent(
        hazardId: 'HZ-001',
        hazardName: 'Real Hazard',
        previousZone: GeofenceZone.preWarning,
        nextZone: GeofenceZone.outside,
        distanceMeters: 1200.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: false,
        evaluatedAt: DateTime.now().toUtc(),
      );

      final (title, body) = GeofenceNotificationService.buildNotificationContent(event);
      expect(title, isEmpty);
      expect(body, isEmpty);
    });
  });

  group('GeofenceNotificationService - Delivery and Cooldown Handling', () {
    late FakeFlutterLocalNotificationsPlugin fakePlugin;
    late GeofenceNotificationService service;

    setUp(() {
      fakePlugin = FakeFlutterLocalNotificationsPlugin();
      service = GeofenceNotificationService(notificationsPlugin: fakePlugin);
    });

    test('Shows notification on eligible transition when cooldown is not suppressed', () async {
      final event = GeofenceTransitionEvent(
        hazardId: 'TEST-HZ-001',
        hazardName: 'Synthetic Hazard',
        previousZone: GeofenceZone.outside,
        nextZone: GeofenceZone.core,
        distanceMeters: 300.0,
        transitionOccurred: true,
        cooldownSuppressed: false,
        isSynthetic: true,
        evaluatedAt: DateTime.now().toUtc(),
      );

      await service.handleTransition(event);

      expect(fakePlugin.initializeCallCount, equals(1));
      expect(fakePlugin.showCallCount, equals(1));
      expect(fakePlugin.lastTitle, equals('[TEST FIXTURE] Safety Alert'));
      expect(fakePlugin.lastBody, equals('Safety alert: you are inside the designated hazard zone.'));
      expect(fakePlugin.lastNotificationDetails?.android?.channelId,
          equals(GeofenceNotificationService.channelId));
    });

    test('Suppresses notification when cooldownSuppressed is true', () async {
      final event = GeofenceTransitionEvent(
        hazardId: 'TEST-HZ-001',
        hazardName: 'Synthetic Hazard',
        previousZone: GeofenceZone.preWarning,
        nextZone: GeofenceZone.core,
        distanceMeters: 300.0,
        transitionOccurred: true,
        cooldownSuppressed: true, // Cooldown active!
        isSynthetic: true,
        evaluatedAt: DateTime.now().toUtc(),
      );

      await service.handleTransition(event);

      expect(fakePlugin.showCallCount, equals(0));
    });

    test('Suppresses notification when transitionOccurred is false', () async {
      final event = GeofenceTransitionEvent(
        hazardId: 'TEST-HZ-001',
        hazardName: 'Synthetic Hazard',
        previousZone: GeofenceZone.core,
        nextZone: GeofenceZone.core,
        distanceMeters: 300.0,
        transitionOccurred: false,
        cooldownSuppressed: false,
        isSynthetic: true,
        evaluatedAt: DateTime.now().toUtc(),
      );

      await service.handleTransition(event);

      expect(fakePlugin.showCallCount, equals(0));
    });
  });
}
