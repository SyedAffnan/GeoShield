/*
 * Cross-validates the dependency-free inverse projection in lcc-epsg7755.mjs
 * against proj4, an independent reference implementation.
 *
 * proj4 is a DEV-ONLY dependency used to prove the hand-written math is right.
 * The preprocessing step itself does not use it, and neither does the backend.
 *
 * Run:  npm install && npm run validate
 */

import proj4 from 'proj4';
import { inverse, forward, parameters } from './lcc-epsg7755.mjs';

const EPSG_7755 = parameters.proj4;
const EPSG_4326 = '+proj=longlat +datum=WGS84 +no_defs';

// Area of use for EPSG:7755 per the EPSG registry: lat 3.87..35.51, lon 65.6..97.42.
const LAT_MIN = 6.0;
const LAT_MAX = 35.5;
const LON_MIN = 66.0;
const LON_MAX = 97.4;

// Known ground-truth WGS 84 locations, used as a human-checkable sanity layer.
const LANDMARKS = [
  { name: 'Bengaluru', lon: 77.5946, lat: 12.9716 },
  { name: 'Mumbai', lon: 72.8777, lat: 19.076 },
  { name: 'Chennai', lon: 80.2707, lat: 13.0827 },
  { name: 'New Delhi', lon: 77.209, lat: 28.6139 },
  { name: 'Kolkata', lon: 88.3639, lat: 22.5726 },
  { name: 'Port Blair', lon: 92.7265, lat: 11.6234 },
  { name: 'Kavaratti', lon: 72.6417, lat: 10.5593 },
  { name: 'Leh', lon: 77.5771, lat: 34.1526 },
];

let maxInverseDeviationMetres = 0;
let maxRoundTripDeviationMetres = 0;
let maxForwardDeviationMetres = 0;
let samples = 0;

/** Rough metres-per-degree at a latitude, for expressing angular error in metres. */
function degreesToMetres(dLon, dLat, atLat) {
  const latMetres = dLat * 111_132.0;
  const lonMetres = dLon * 111_320.0 * Math.cos(atLat * Math.PI / 180);
  return Math.hypot(lonMetres, latMetres);
}

console.log('EPSG:7755 derived constants');
console.log(`  cone constant n      : ${parameters.n}`);
console.log(`  F                    : ${parameters.bigF}`);
console.log(`  r at false origin    : ${parameters.rFalseOrigin}`);
console.log('');

// 1. Grid sweep across the full area of use.
for (let lat = LAT_MIN; lat <= LAT_MAX; lat += 0.25) {
  for (let lon = LON_MIN; lon <= LON_MAX; lon += 0.25) {
    // Reference forward, via proj4.
    const [refEast, refNorth] = proj4(EPSG_4326, EPSG_7755, [lon, lat]);

    // Our forward must agree with proj4's forward.
    const [ourEast, ourNorth] = forward(lon, lat);
    maxForwardDeviationMetres = Math.max(
      maxForwardDeviationMetres,
      Math.hypot(ourEast - refEast, ourNorth - refNorth),
    );

    // Our inverse of proj4's forward must agree with proj4's inverse of it.
    const [refLon, refLat] = proj4(EPSG_7755, EPSG_4326, [refEast, refNorth]);
    const [ourLon, ourLat] = inverse(refEast, refNorth);

    maxInverseDeviationMetres = Math.max(
      maxInverseDeviationMetres,
      degreesToMetres(ourLon - refLon, ourLat - refLat, lat),
    );

    // Our inverse must recover the original geographic coordinate.
    maxRoundTripDeviationMetres = Math.max(
      maxRoundTripDeviationMetres,
      degreesToMetres(ourLon - lon, ourLat - lat, lat),
    );

    samples += 1;
  }
}

console.log(`Grid sweep over lat ${LAT_MIN}..${LAT_MAX}, lon ${LON_MIN}..${LON_MAX} (${samples} points)`);
console.log(`  max |ours - proj4| forward : ${maxForwardDeviationMetres.toExponential(3)} m`);
console.log(`  max |ours - proj4| inverse : ${maxInverseDeviationMetres.toExponential(3)} m`);
console.log(`  max round-trip error       : ${maxRoundTripDeviationMetres.toExponential(3)} m`);
console.log('');

// 2. Landmark check, so the numbers are legible to a human reviewer.
console.log('Landmark round-trip (WGS84 -> EPSG:7755 -> WGS84 via our inverse)');
let maxLandmarkDeviationMetres = 0;
for (const { name, lon, lat } of LANDMARKS) {
  const [east, north] = proj4(EPSG_4326, EPSG_7755, [lon, lat]);
  const [gotLon, gotLat] = inverse(east, north);
  const deviation = degreesToMetres(gotLon - lon, gotLat - lat, lat);
  maxLandmarkDeviationMetres = Math.max(maxLandmarkDeviationMetres, deviation);
  console.log(
    `  ${name.padEnd(11)} E=${east.toFixed(1).padStart(11)} N=${north.toFixed(1).padStart(11)}`
    + ` -> ${gotLon.toFixed(6)}, ${gotLat.toFixed(6)}  (${deviation.toExponential(2)} m)`,
  );
}
console.log('');

// A tolerance far below the 6-decimal-place (~0.11 m) output precision, and far
// below the 2 m accuracy of the WGS 84 ensemble datum itself.
const TOLERANCE_METRES = 1e-6;
const worst = Math.max(
  maxForwardDeviationMetres,
  maxInverseDeviationMetres,
  maxRoundTripDeviationMetres,
  maxLandmarkDeviationMetres,
);

if (worst > TOLERANCE_METRES) {
  console.error(`FAIL: worst deviation ${worst.toExponential(3)} m exceeds ${TOLERANCE_METRES} m`);
  process.exit(1);
}
console.log(`PASS: worst deviation ${worst.toExponential(3)} m is within ${TOLERANCE_METRES} m of proj4.`);
