import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import '../models/geofence_zone.dart';
import 'geofencing_engine.dart';

/// Notification service delivering local push notifications for geofence transitions.
///
/// Rules:
/// - Channel: `geoshield_hazard_geofence`
/// - PRE_WARNING: "Safety warning: approaching a designated hazard zone."
/// - CORE: "Safety alert: you are inside the designated hazard zone."
/// - Synthetic alerts visibly contain: `[TEST FIXTURE]`.
/// - Alerts are only displayed when transition occurred and cooldown is not active.
class GeofenceNotificationService {
  GeofenceNotificationService({
    FlutterLocalNotificationsPlugin? notificationsPlugin,
  }) : _plugin = notificationsPlugin ?? FlutterLocalNotificationsPlugin();

  final FlutterLocalNotificationsPlugin _plugin;
  bool _initialized = false;

  static const String channelId = 'geoshield_hazard_geofence';
  static const String channelName = 'Safety Hazard Geofence Alerts';
  static const String channelDescription =
      'Real-time proximity alerts when approaching or entering designated road hazard zones.';

  static const AndroidNotificationDetails androidDetails =
      AndroidNotificationDetails(
    channelId,
    channelName,
    channelDescription: channelDescription,
    importance: Importance.high,
    priority: Priority.high,
    icon: '@mipmap/ic_launcher',
  );

  static const NotificationDetails notificationDetails = NotificationDetails(
    android: androidDetails,
  );

  /// Initializes notification plugin and channel settings.
  Future<void> initialize() async {
    if (_initialized) return;

    const androidSettings =
        AndroidInitializationSettings('@mipmap/ic_launcher');
    const initSettings = InitializationSettings(android: androidSettings);

    await _plugin.initialize(settings: initSettings);

    final androidPlugin = _plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();
    await androidPlugin?.requestNotificationsPermission();
    await androidPlugin?.createNotificationChannel(
      const AndroidNotificationChannel(
        channelId,
        channelName,
        description: channelDescription,
        importance: Importance.high,
      ),
    );

    _initialized = true;
  }

  /// Processes a transition event and shows a local notification if eligible and not suppressed.
  Future<void> handleTransition(GeofenceTransitionEvent event) async {
    if (!event.transitionOccurred || event.cooldownSuppressed) {
      return;
    }

    if (!_initialized) {
      await initialize();
    }

    final (String title, String body) = buildNotificationContent(event);

    if (title.isEmpty || body.isEmpty) {
      return;
    }

    // Stable notification ID derived from hazard ID hashCode
    final notificationId = event.hazardId.hashCode.abs() % 100000;

    await _plugin.show(
      id: notificationId,
      title: title,
      body: body,
      notificationDetails: notificationDetails,
    );
  }

  /// Generates the factual notification text adhering to project requirements.
  static (String title, String body) buildNotificationContent(
      GeofenceTransitionEvent event) {
    if (event.nextZone == GeofenceZone.core) {
      final prefix = event.isSynthetic ? '[TEST FIXTURE] ' : '';
      return (
        '${prefix}Safety Alert',
        'Safety alert: you are inside the designated hazard zone.',
      );
    } else if (event.nextZone == GeofenceZone.preWarning) {
      final prefix = event.isSynthetic ? '[TEST FIXTURE] ' : '';
      return (
        '${prefix}Safety Warning',
        'Safety warning: approaching a designated hazard zone.',
      );
    } else {
      // De-escalation to outside does not emit an acute notification
      return ('', '');
    }
  }
}
