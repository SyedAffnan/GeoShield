/*
 * Validates the DERIVED resource (after reproject + simplify + round) by doing
 * WGS84 point-in-polygon on known landmarks. This proves the whole pipeline
 * produced a usable resource, independently of the Java implementation that
 * will consume it. Uses only the derived file - no proj4, no source file.
 */

import { readFileSync } from 'node:fs';

const [, , resourcePath] = process.argv;
const fc = JSON.parse(readFileSync(resourcePath, 'utf8'));

// Ray casting for a single linear ring; [lon, lat] vertices.
function pointInRing(lon, lat, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i, i += 1) {
    const xi = ring[i][0];
    const yi = ring[i][1];
    const xj = ring[j][0];
    const yj = ring[j][1];
    const intersects = (yi > lat) !== (yj > lat)
      && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi;
    if (intersects) {
      inside = !inside;
    }
  }
  return inside;
}

// A point is in a polygon if it is in the outer ring and in no hole.
function pointInPolygon(lon, lat, polygon) {
  if (!pointInRing(lon, lat, polygon[0])) {
    return false;
  }
  for (let h = 1; h < polygon.length; h += 1) {
    if (pointInRing(lon, lat, polygon[h])) {
      return false;
    }
  }
  return true;
}

function resolve(lon, lat) {
  for (const feature of fc.features) {
    for (const polygon of feature.geometry.coordinates) {
      if (pointInPolygon(lon, lat, polygon)) {
        return feature.properties.state_name;
      }
    }
  }
  return null;
}

const CASES = [
  { name: 'Bengaluru', lon: 77.5946, lat: 12.9716, expect: 'Karnataka' },
  { name: 'Mumbai', lon: 72.8777, lat: 19.076, expect: 'Maharashtra' },
  { name: 'Chennai', lon: 80.2707, lat: 13.0827, expect: 'Tamil Nadu' },
  { name: 'New Delhi', lon: 77.209, lat: 28.6139, expect: 'Delhi' },
  { name: 'Jaipur', lon: 75.7873, lat: 26.9124, expect: 'Rajasthan' },
  { name: 'Kolkata', lon: 88.3639, lat: 22.5726, expect: 'West Bengal' },
  { name: 'Hyderabad', lon: 78.4867, lat: 17.385, expect: 'Telangana' },
  { name: 'Panaji', lon: 73.8278, lat: 15.4989, expect: 'Goa' },
  { name: 'Port Blair', lon: 92.7265, lat: 11.6234, expect: 'Andaman and Nicobar Islands' },
  { name: 'Itanagar', lon: 93.6053, lat: 27.0844, expect: 'Arunachal Pradesh' },
  { name: 'Srinagar', lon: 74.7973, lat: 34.0837, expect: 'Jammu and Kashmir' },
  { name: 'Daman', lon: 72.8328, lat: 20.3974, expect: 'Dadra and Nagar Haveli and Daman and Diu' },
  { name: 'Bay of Bengal (offshore)', lon: 85.0, lat: 15.0, expect: null },
  { name: 'Arabian Sea (offshore)', lon: 68.0, lat: 15.0, expect: null },
];

let failures = 0;
for (const c of CASES) {
  const got = resolve(c.lon, c.lat);
  const ok = got === c.expect;
  if (!ok) {
    failures += 1;
  }
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${c.name.padEnd(26)} -> ${got ?? '(unresolved)'}`
    + (ok ? '' : `   expected ${c.expect ?? '(unresolved)'}`));
}

console.log('');
console.log(`state_name values in derived resource (${fc.features.length}):`);
console.log('  ' + fc.features.map((f) => f.properties.state_name).sort().join('\n  '));

process.exit(failures === 0 ? 0 : 1);
