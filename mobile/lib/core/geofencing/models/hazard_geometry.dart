import '../haversine_distance.dart';

/// Spatial hazard geometry abstraction for geofence evaluation.
///
/// In production, no real hazard geometries are available.
/// All test fixtures must have `isSynthetic = true` and
/// `dataSource = "TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION"`.
final class HazardGeometry {
  const HazardGeometry({
    required this.id,
    required this.name,
    required this.latitude,
    required this.longitude,
    this.coreRadiusMeters = 500.0,
    this.preWarningRadiusMeters = 1000.0,
    this.isSynthetic = false,
    this.dataSource = 'UNKNOWN',
    this.isActive = true,
  });

  final String id;
  final String name;
  final double latitude;
  final double longitude;
  final double coreRadiusMeters;
  final double preWarningRadiusMeters;
  final bool isSynthetic;
  final String dataSource;
  final bool isActive;

  /// Creates a validated point hazard.
  factory HazardGeometry.point({
    required String id,
    required String name,
    required double latitude,
    required double longitude,
    double coreRadiusMeters = 500.0,
    double preWarningRadiusMeters = 1000.0,
    bool isSynthetic = false,
    String dataSource = 'UNKNOWN',
    bool isActive = true,
  }) {
    HaversineDistance.validateCoordinates(latitude, longitude);
    if (coreRadiusMeters <= 0.0 || preWarningRadiusMeters <= 0.0) {
      throw ArgumentError('Radii must be positive numbers');
    }
    if (coreRadiusMeters >= preWarningRadiusMeters) {
      throw ArgumentError('Core radius must be smaller than pre-warning radius');
    }
    return HazardGeometry(
      id: id,
      name: name,
      latitude: latitude,
      longitude: longitude,
      coreRadiusMeters: coreRadiusMeters,
      preWarningRadiusMeters: preWarningRadiusMeters,
      isSynthetic: isSynthetic,
      dataSource: dataSource,
      isActive: isActive,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is HazardGeometry &&
          runtimeType == other.runtimeType &&
          id == other.id &&
          name == other.name &&
          latitude == other.latitude &&
          longitude == other.longitude &&
          coreRadiusMeters == other.coreRadiusMeters &&
          preWarningRadiusMeters == other.preWarningRadiusMeters &&
          isSynthetic == other.isSynthetic &&
          dataSource == other.dataSource &&
          isActive == other.isActive;

  @override
  int get hashCode => Object.hash(
        id,
        name,
        latitude,
        longitude,
        coreRadiusMeters,
        preWarningRadiusMeters,
        isSynthetic,
        dataSource,
        isActive,
      );

  @override
  String toString() =>
      'HazardGeometry(id: $id, name: $name, lat: $latitude, lon: $longitude, '
      'core: $coreRadiusMeters m, preWarning: $preWarningRadiusMeters m, '
      'isSynthetic: $isSynthetic, dataSource: $dataSource, active: $isActive)';
}
