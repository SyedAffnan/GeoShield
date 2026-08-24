/*
 * Inverse map projection for EPSG:7755 ("WGS 84 / India NSF LCC").
 *
 * Method : Lambert Conic Conformal (2SP), EPSG method code 9802.
 * Source : EPSG Geodetic Parameter Registry, entry 7755.
 *          +proj=lcc +lat_0=24 +lon_0=80 +lat_1=12.472955
 *          +lat_2=35.1728044444444 +x_0=4000000 +y_0=4000000
 *          +datum=WGS84 +units=m +no_defs
 *
 * The base geographic CRS of EPSG:7755 is WGS 84 (EPSG:4326), so converting
 * EPSG:7755 -> EPSG:4326 is a pure inverse map projection. There is NO datum
 * shift and therefore no transformation error: the conversion is exact to
 * double-precision arithmetic, limited only by floating point.
 *
 * Formulas follow Snyder, "Map Projections - A Working Manual" (USGS
 * Professional Paper 1395), pp. 107-109, which is the same derivation used by
 * EPSG Guidance Note 7-2 for method 9802.
 *
 * This module is intentionally dependency-free so the preprocessing step stays
 * reproducible without a package install. It is cross-validated against proj4
 * by tools/geodata/validate-projection.mjs.
 */

// WGS 84 ellipsoid.
const A = 6378137.0;
const INVERSE_FLATTENING = 298.257223563;
const F = 1 / INVERSE_FLATTENING;
const E2 = 2 * F - F * F;
const E = Math.sqrt(E2);

// EPSG:7755 projection parameters.
const LATITUDE_OF_FALSE_ORIGIN = 24.0;
const LONGITUDE_OF_FALSE_ORIGIN = 80.0;
const STANDARD_PARALLEL_1 = 12.472955;
const STANDARD_PARALLEL_2 = 35.1728044444444;
const EASTING_AT_FALSE_ORIGIN = 4000000.0;
const NORTHING_AT_FALSE_ORIGIN = 4000000.0;

const DEG_TO_RAD = Math.PI / 180;
const RAD_TO_DEG = 180 / Math.PI;

/** Snyder eq. 14-15: m = cos(phi) / sqrt(1 - e^2 sin^2(phi)). */
function m(phi) {
  const s = Math.sin(phi);
  return Math.cos(phi) / Math.sqrt(1 - E2 * s * s);
}

/** Snyder eq. 15-9: t = tan(pi/4 - phi/2) / ((1 - e sin phi)/(1 + e sin phi))^(e/2). */
function t(phi) {
  const s = Math.sin(phi);
  return Math.tan(Math.PI / 4 - phi / 2) / Math.pow((1 - E * s) / (1 + E * s), E / 2);
}

const phi0 = LATITUDE_OF_FALSE_ORIGIN * DEG_TO_RAD;
const lambda0 = LONGITUDE_OF_FALSE_ORIGIN * DEG_TO_RAD;
const phi1 = STANDARD_PARALLEL_1 * DEG_TO_RAD;
const phi2 = STANDARD_PARALLEL_2 * DEG_TO_RAD;

// Snyder eq. 15-8 (cone constant n), 15-10 (F), 15-7a (radius at false origin).
const N = (Math.log(m(phi1)) - Math.log(m(phi2))) / (Math.log(t(phi1)) - Math.log(t(phi2)));
const BIG_F = m(phi1) / (N * Math.pow(t(phi1), N));
const R_FALSE_ORIGIN = A * BIG_F * Math.pow(t(phi0), N);

/** Constants exposed for the validation harness. */
export const parameters = Object.freeze({
  a: A,
  inverseFlattening: INVERSE_FLATTENING,
  n: N,
  bigF: BIG_F,
  rFalseOrigin: R_FALSE_ORIGIN,
  proj4: '+proj=lcc +lat_0=24 +lon_0=80 +lat_1=12.472955 +lat_2=35.1728044444444 '
    + '+x_0=4000000 +y_0=4000000 +datum=WGS84 +units=m +no_defs',
});

/**
 * Forward projection: WGS 84 [longitude, latitude] in degrees ->
 * EPSG:7755 [easting, northing] in metres. Present so the validation harness
 * can round-trip, and so the inverse can be checked against it.
 */
export function forward(longitudeDegrees, latitudeDegrees) {
  const phi = latitudeDegrees * DEG_TO_RAD;
  const lambda = longitudeDegrees * DEG_TO_RAD;
  const r = A * BIG_F * Math.pow(t(phi), N);
  const theta = N * (lambda - lambda0);
  return [
    EASTING_AT_FALSE_ORIGIN + r * Math.sin(theta),
    NORTHING_AT_FALSE_ORIGIN + R_FALSE_ORIGIN - r * Math.cos(theta),
  ];
}

/**
 * Inverse projection: EPSG:7755 [easting, northing] in metres ->
 * WGS 84 [longitude, latitude] in degrees.
 *
 * Returns longitude first to match GeoJSON (RFC 7946) axis order.
 */
export function inverse(easting, northing) {
  const dEasting = easting - EASTING_AT_FALSE_ORIGIN;
  const dNorthing = R_FALSE_ORIGIN - (northing - NORTHING_AT_FALSE_ORIGIN);
  const sign = N < 0 ? -1 : 1;

  // Snyder eq. 14-10 / 14-11, with the sign of n applied per p. 108.
  const r = sign * Math.hypot(dEasting, dNorthing);
  const tPrime = Math.pow(r / (A * BIG_F), 1 / N);
  const theta = Math.atan2(sign * dEasting, sign * dNorthing);

  const lambda = theta / N + lambda0;

  // Snyder eq. 7-9: iterate phi to convergence. Converges in a handful of
  // passes for terrestrial latitudes; the guard bounds it regardless.
  let phi = Math.PI / 2 - 2 * Math.atan(tPrime);
  for (let i = 0; i < 16; i += 1) {
    const s = Math.sin(phi);
    const next = Math.PI / 2
      - 2 * Math.atan(tPrime * Math.pow((1 - E * s) / (1 + E * s), E / 2));
    const converged = Math.abs(next - phi) < 1e-15;
    phi = next;
    if (converged) {
      break;
    }
  }

  return [lambda * RAD_TO_DEG, phi * RAD_TO_DEG];
}
