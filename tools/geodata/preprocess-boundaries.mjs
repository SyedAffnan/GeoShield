/*
 * Offline preprocessing for the GeoShield State/UT boundary resource.
 *
 * Input  : the Survey of India / NWIC "state_NWIC" GeoJSON (EPSG:7755, metres,
 *          9 properties per feature, ~15-dp coordinates, ~47 MiB).
 * Output : a DERIVED GeoJSON bundled as a backend classpath resource
 *          (EPSG:4326 degrees, 2 properties per feature, 6-dp coordinates,
 *          conservatively simplified).
 *
 * The input path is supplied on the command line so no personal absolute path
 * is baked into the repository. Nothing here runs at application build or
 * runtime; this is a one-time, reproducible derivation.
 *
 * Steps, in order, per feature:
 *   1. Reproject every vertex EPSG:7755 -> EPSG:4326 (exact inverse LCC).
 *   2. Ramer-Douglas-Peucker simplify each ring at a conservative tolerance,
 *      never below 4 points, so no ring (including small islands) is dropped.
 *   3. Round survivors to 6 decimal places (~0.11 m).
 *   4. Keep only state_name and stcode; apply the four explicit name mappings.
 *
 * Run:  node --max-old-space-size=4096 preprocess-boundaries.mjs \
 *         "<source .GeoJSON>" ../../backend/src/main/resources/geo/india-state-ut-boundaries.geojson
 */

import { readFileSync, writeFileSync, statSync } from 'node:fs';
import { inverse } from './lcc-epsg7755.mjs';

// ---- Step 4 config: explicit, exhaustive State/UT name normalization --------
// GeoJSON state_name (left) -> exact MoRTH geographicUnit (right).
// Only non-identical names appear here. NO fuzzy matching, NO global '&' rule:
// each entry is individually justified in the block comment.
const STATE_NAME_MAPPINGS = Object.freeze({
  // '&' -> 'and', and singular 'Island' -> plural 'Islands'.
  'Andaman & Nicobar Island': 'Andaman and Nicobar Islands',
  // Source misspelling 'Arunanchal' (extra 'n') -> correct 'Arunachal'.
  'Arunanchal Pradesh': 'Arunachal Pradesh',
  // '&' -> 'and' (twice), and 'Havelli' (double-l) -> 'Haveli'.
  'Dadra & Nagar Havelli and Daman & Diu': 'Dadra and Nagar Haveli and Daman and Diu',
  // '&' -> 'and'.
  'Jammu & Kashmir': 'Jammu and Kashmir',
});

// The 32 names expected to already be byte-identical to MoRTH. Any GeoJSON name
// that is neither in the mapping nor in this set is a surprise and aborts the
// run, so the mapping can never silently drift out of sync with the source.
const IDENTICAL_NAMES = new Set([
  'Andhra Pradesh', 'Assam', 'Bihar', 'Chandigarh', 'Chhattisgarh', 'Delhi',
  'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jharkhand', 'Karnataka',
  'Kerala', 'Ladakh', 'Lakshadweep', 'Madhya Pradesh', 'Maharashtra', 'Manipur',
  'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Puducherry', 'Punjab',
  'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh',
  'Uttarakhand', 'West Bengal',
]);

// ---- Step 2/3 config --------------------------------------------------------
// Douglas-Peucker tolerance in degrees. 0.0001 deg ~= 11 m at Indian latitudes,
// i.e. the maximum a boundary vertex can move. That is below consumer-GPS error
// and immaterial for State/UT-level resolution. Rings never drop below 4 points.
const SIMPLIFY_TOLERANCE_DEGREES = 0.0001;
const MIN_RING_POINTS = 4;
const OUTPUT_DECIMALS = 6;

const [, , sourcePath, outputPath] = process.argv;
if (!sourcePath || !outputPath) {
  console.error('Usage: node preprocess-boundaries.mjs <source.GeoJSON> <output.geojson>');
  process.exit(2);
}

function roundCoordinate(value) {
  return Number(value.toFixed(OUTPUT_DECIMALS));
}

/** Perpendicular distance from p to the segment a-b, in degree units. */
function perpendicularDistance(p, a, b) {
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  if (dx === 0 && dy === 0) {
    return Math.hypot(p[0] - a[0], p[1] - a[1]);
  }
  const tParam = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy);
  const clamped = Math.max(0, Math.min(1, tParam));
  const projX = a[0] + clamped * dx;
  const projY = a[1] + clamped * dy;
  return Math.hypot(p[0] - projX, p[1] - projY);
}

/** Iterative Ramer-Douglas-Peucker over an open point list. */
function douglasPeucker(points, tolerance) {
  if (points.length < 3) {
    return points.slice();
  }
  const keep = new Array(points.length).fill(false);
  keep[0] = true;
  keep[points.length - 1] = true;
  const stack = [[0, points.length - 1]];
  while (stack.length > 0) {
    const [start, end] = stack.pop();
    let maxDistance = 0;
    let index = -1;
    for (let i = start + 1; i < end; i += 1) {
      const distance = perpendicularDistance(points[i], points[start], points[end]);
      if (distance > maxDistance) {
        maxDistance = distance;
        index = i;
      }
    }
    if (maxDistance > tolerance && index !== -1) {
      keep[index] = true;
      stack.push([start, index]);
      stack.push([index, end]);
    }
  }
  return points.filter((_, i) => keep[i]);
}

/**
 * Simplify one closed ring: reproject already done by caller. Keeps the ring
 * closed and enforces a floor of MIN_RING_POINTS so small islands survive.
 */
function simplifyRing(ring, sourceVertexCounter) {
  sourceVertexCounter.count += ring.length;
  // Ring is closed: first == last. Simplify the open path, then re-close.
  const closed = ring.length > 1
    && ring[0][0] === ring[ring.length - 1][0]
    && ring[0][1] === ring[ring.length - 1][1];
  const open = closed ? ring.slice(0, -1) : ring.slice();

  let tolerance = SIMPLIFY_TOLERANCE_DEGREES;
  let simplifiedOpen = douglasPeucker([...open, open[0]], tolerance).slice(0, -1);

  // If the conservative tolerance somehow over-thins a ring below the floor,
  // relax it until enough points remain, so we never emit a degenerate ring.
  while (simplifiedOpen.length < MIN_RING_POINTS - 1 && open.length >= MIN_RING_POINTS - 1) {
    tolerance /= 4;
    simplifiedOpen = douglasPeucker([...open, open[0]], tolerance).slice(0, -1);
    if (tolerance < 1e-9) {
      simplifiedOpen = open;
      break;
    }
  }

  const rounded = simplifiedOpen.map(([x, y]) => [roundCoordinate(x), roundCoordinate(y)]);
  rounded.push([rounded[0][0], rounded[0][1]]); // re-close
  return rounded;
}

function reprojectRing(ring) {
  return ring.map(([easting, northing]) => inverse(easting, northing));
}

console.log(`Reading source: ${sourcePath}`);
const sourceBytes = statSync(sourcePath).size;
const source = JSON.parse(readFileSync(sourcePath, 'utf8'));
if (source.type !== 'FeatureCollection' || !Array.isArray(source.features)) {
  console.error('Source is not a GeoJSON FeatureCollection.');
  process.exit(1);
}
console.log(`  ${(sourceBytes / 1048576).toFixed(1)} MiB, ${source.features.length} features`);
console.log(`  declared CRS: ${source.crs?.properties?.name ?? '(none)'}`);
console.log('');

const sourceVertexCounter = { count: 0 };
let outputVertices = 0;
const seenNames = new Set();

const outputFeatures = source.features.map((feature) => {
  const rawName = feature.properties?.state_name;
  if (typeof rawName !== 'string') {
    throw new Error(`Feature ${feature.properties?.id} is missing state_name.`);
  }
  const trimmed = rawName.trim();

  // Resolve the MoRTH-canonical name via the explicit mapping only.
  let canonical;
  if (Object.prototype.hasOwnProperty.call(STATE_NAME_MAPPINGS, trimmed)) {
    canonical = STATE_NAME_MAPPINGS[trimmed];
  } else if (IDENTICAL_NAMES.has(trimmed)) {
    canonical = trimmed;
  } else {
    throw new Error(`Unrecognized GeoJSON state_name "${trimmed}" - update the explicit mapping.`);
  }
  seenNames.add(trimmed);

  if (feature.geometry?.type !== 'MultiPolygon') {
    throw new Error(`Feature "${trimmed}" is ${feature.geometry?.type}, expected MultiPolygon.`);
  }

  const polygons = feature.geometry.coordinates.map((polygon) => polygon.map((ring) => {
    const reprojected = reprojectRing(ring);
    const simplified = simplifyRing(reprojected, sourceVertexCounter);
    outputVertices += simplified.length;
    return simplified;
  }));

  return {
    type: 'Feature',
    properties: {
      state_name: canonical,
      stcode: feature.properties.stcode,
    },
    geometry: { type: 'MultiPolygon', coordinates: polygons },
  };
});

// Guard: every explicit mapping key must have been present in the source.
for (const key of Object.keys(STATE_NAME_MAPPINGS)) {
  if (!seenNames.has(key)) {
    throw new Error(`Explicit mapping key "${key}" never matched a source feature.`);
  }
}

const output = {
  type: 'FeatureCollection',
  name: 'india-state-ut-boundaries',
  _derivedFrom: 'Survey of India (SOI) / NWIC "state_NWIC" administrative boundaries',
  _derivation: 'Reprojected EPSG:7755 -> EPSG:4326; simplified (Douglas-Peucker '
    + `${SIMPLIFY_TOLERANCE_DEGREES} deg); rounded to ${OUTPUT_DECIMALS} dp; `
    + 'properties reduced to state_name (normalized to MoRTH) and stcode.',
  crs: { type: 'name', properties: { name: 'urn:ogc:def:crs:OGC:1.3:CRS84' } },
  features: outputFeatures,
};

writeFileSync(outputPath, JSON.stringify(output));
const outBytes = statSync(outputPath).size;

console.log('Preprocessing complete.');
console.log(`  features written    : ${outputFeatures.length}`);
console.log(`  source vertices     : ${sourceVertexCounter.count.toLocaleString()}`);
console.log(`  output vertices     : ${outputVertices.toLocaleString()}`
  + ` (${(100 * (1 - outputVertices / sourceVertexCounter.count)).toFixed(1)}% reduction)`);
console.log(`  simplify tolerance  : ${SIMPLIFY_TOLERANCE_DEGREES} deg (~${Math.round(SIMPLIFY_TOLERANCE_DEGREES * 111320)} m)`);
console.log(`  output precision    : ${OUTPUT_DECIMALS} dp (~0.11 m)`);
console.log(`  output size         : ${(outBytes / 1048576).toFixed(2)} MiB`
  + ` (from ${(sourceBytes / 1048576).toFixed(1)} MiB)`);
console.log(`  written to          : ${outputPath}`);
