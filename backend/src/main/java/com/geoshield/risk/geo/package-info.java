/**
 * Offline, deterministic GPS -> State/UT geographic resolution for the Risk module.
 *
 * <p>Boundaries come from the bundled classpath resource
 * {@code geo/india-state-ut-boundaries.geojson}, a derived artifact reprojected
 * to WGS84 from the Survey of India / NWIC source (see the resource's provenance
 * fields and {@code tools/geodata}). Resolution is point-in-polygon (ray casting)
 * with MultiPolygon and polygon-hole handling. There is no dependency on any
 * absolute filesystem path and no external geocoding service.
 */
package com.geoshield.risk.geo;
