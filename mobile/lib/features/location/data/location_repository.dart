import '../../../core/location/device_location_service.dart';
import '../../../core/network/api_client.dart';

/// The tourist location the backend currently holds, as returned by
/// `/api/v1/locations` (`LocationResponse`).
class CurrentLocation {
  const CurrentLocation(
      {required this.latitude,
      required this.longitude,
      required this.timestamp});
  final double latitude;
  final double longitude;
  final DateTime? timestamp;

  factory CurrentLocation.fromJson(Map<String, dynamic> json) =>
      CurrentLocation(
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        timestamp: DateTime.tryParse(json['timestamp'] as String? ?? ''),
      );
}

class LocationRepository {
  LocationRepository(this._client);
  final GeoShieldApiClient _client;

  /// Sends a real device fix to the existing `POST /api/v1/locations` endpoint and
  /// returns the location the backend stored. Field names and types mirror the
  /// server's `LocationRequest`; no coordinate is defaulted or synthesized.
  Future<CurrentLocation> submitCurrentLocation(DeviceLocationFix fix) async {
    return CurrentLocation.fromJson(
      await _client.postData('/api/v1/locations', data: <String, dynamic>{
        'latitude': fix.latitude,
        'longitude': fix.longitude,
        if (fix.accuracy != null) 'accuracy': fix.accuracy,
        if (fix.speed != null) 'speed': fix.speed,
        'timestamp': fix.timestamp.toUtc().toIso8601String(),
      }),
    );
  }

  /// Reads the location already stored for the authenticated tourist.
  Future<CurrentLocation> getCurrentLocation() async =>
      CurrentLocation.fromJson(await _client.getData('/api/v1/locations') as Map<String, dynamic>);
}
