import '../models/hazard_geometry.dart';

/// Provider interface supplying active hazard geometries for mobile geofencing.
abstract interface class HazardGeometryProvider {
  Future<List<HazardGeometry>> getActiveHazards();
}

/// Production provider returning an empty list because no validated
/// open coordinate-bearing black-spot dataset is available for production ingestion.
final class EmptyProductionHazardProvider implements HazardGeometryProvider {
  const EmptyProductionHazardProvider();

  @override
  Future<List<HazardGeometry>> getActiveHazards() async => const [];
}

/// Test/development provider supplying synthetic test fixtures.
/// Every synthetic hazard is explicitly marked `isSynthetic = true` and
/// `dataSource = "TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION"`.
final class SyntheticTestHazardProvider implements HazardGeometryProvider {
  SyntheticTestHazardProvider([List<HazardGeometry>? fixtures])
      : _fixtures = fixtures ?? defaultFixtures;

  final List<HazardGeometry> _fixtures;

  static final List<HazardGeometry> defaultFixtures = [
    HazardGeometry.point(
      id: 'TEST-HZ-001',
      name: 'Synthetic High-Risk Curve',
      latitude: 28.6139,
      longitude: 77.2090,
      coreRadiusMeters: 500.0,
      preWarningRadiusMeters: 1000.0,
      isSynthetic: true,
      dataSource: 'TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION',
      isActive: true,
    ),
    HazardGeometry.point(
      id: 'TEST-HZ-002',
      name: 'Synthetic Intersection Hazard',
      latitude: 28.6250,
      longitude: 77.2150,
      coreRadiusMeters: 500.0,
      preWarningRadiusMeters: 1000.0,
      isSynthetic: true,
      dataSource: 'TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION',
      isActive: true,
    ),
  ];

  @override
  Future<List<HazardGeometry>> getActiveHazards() async => _fixtures;
}
