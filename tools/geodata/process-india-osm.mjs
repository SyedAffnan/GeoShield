import fs from 'fs';
import readline from 'readline';

// 1. Load India state boundaries
const geojson = JSON.parse(fs.readFileSync('backend/src/main/resources/geo/india-state-ut-boundaries.geojson'));

const states = geojson.features.map(f => {
  let minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
  function getBbox(coords) {
    if (typeof coords[0] === 'number') {
      const [lon, lat] = coords;
      if (lat < minLat) minLat = lat;
      if (lat > maxLat) maxLat = lat;
      if (lon < minLon) minLon = lon;
      if (lon > maxLon) maxLon = lon;
    } else {
      coords.forEach(getBbox);
    }
  }
  getBbox(f.geometry.coordinates);
  return {
    name: f.properties.state_name,
    code: f.properties.stcode,
    geometry: f.geometry,
    bbox: { minLat, maxLat, minLon, maxLon }
  };
});

function pointInPolygon(pt, ring) {
  let inside = false;
  const x = pt[0], y = pt[1];
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0], yi = ring[i][1];
    const xj = ring[j][0], yj = ring[j][1];
    const intersect = ((yi > y) !== (yj > y)) &&
      (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
    if (intersect) inside = !inside;
  }
  return inside;
}

function findStateForPoint(lat, lon) {
  for (const s of states) {
    if (lat < s.bbox.minLat || lat > s.bbox.maxLat || lon < s.bbox.minLon || lon > s.bbox.maxLon) {
      continue;
    }
    const geom = s.geometry;
    if (geom.type === 'Polygon') {
      for (const ring of geom.coordinates) {
        if (pointInPolygon([lon, lat], ring)) return s.name;
      }
    } else if (geom.type === 'MultiPolygon') {
      for (const poly of geom.coordinates) {
        for (const ring of poly) {
          if (pointInPolygon([lon, lat], ring)) return s.name;
        }
      }
    }
  }
  return null;
}

function getCentroid(geom) {
  if (!geom || !geom.coordinates) return null;
  if (geom.type === 'Point') {
    return { lon: geom.coordinates[0], lat: geom.coordinates[1] };
  }
  let sumLat = 0, sumLon = 0, count = 0;
  function add(coords) {
    if (typeof coords[0] === 'number') {
      sumLon += coords[0];
      sumLat += coords[1];
      count++;
    } else {
      coords.forEach(add);
    }
  }
  add(geom.coordinates);
  if (count === 0) return null;
  return { lon: sumLon / count, lat: sumLat / count };
}

function mapCenterType(amenity) {
  switch (amenity) {
    case 'hospital':
      return 'MEDICAL';
    case 'police':
      return 'RESPONSE_UNIT';
    case 'fire_station':
      return 'FIRE';
    default:
      return null;
  }
}

async function run() {
  console.log('Processing full-country OpenStreetMap emergency facilities...');
  const fileStream = fs.createReadStream('tools/geodata/downloads/poi/points_of_interest.geojson');
  const rl = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity
  });

  let totalRawRecords = 0;
  let missingCoords = 0;
  let missingNames = 0;
  let outsideBbox = 0;
  let outsideStatePolygon = 0;
  let duplicateOsmIds = 0;
  let duplicateCoords = 0;

  const seenIds = new Set();
  const seenCoords = new Set();
  const retained = [];
  const stateDistribution = {};
  const typeCounts = { MEDICAL: 0, RESPONSE_UNIT: 0, FIRE: 0 };
  const geometryCounts = { Point: 0, Polygon: 0, MultiPolygon: 0, Other: 0 };

  for await (const line of rl) {
    if (!line.includes('"type": "Feature"')) continue;
    try {
      const cleaned = line.trim().replace(/,$/, '');
      const feat = JSON.parse(cleaned);
      const p = feat.properties || {};
      const centerType = mapCenterType(p.amenity);
      if (!centerType) continue;

      totalRawRecords++;

      const rawName = p.name || p.name_en || p.name_latin;
      if (!rawName || !rawName.trim()) {
        missingNames++;
        continue;
      }
      const name = rawName.trim();

      const centroid = getCentroid(feat.geometry);
      if (!centroid || isNaN(centroid.lat) || isNaN(centroid.lon)) {
        missingCoords++;
        continue;
      }
      const lat = centroid.lat;
      const lon = centroid.lon;

      // Coarse Bbox check
      if (lat < 6.0 || lat > 38.0 || lon < 68.0 || lon > 98.0) {
        outsideBbox++;
        continue;
      }

      // Exact Point-in-polygon check
      const verifiedState = findStateForPoint(lat, lon);
      if (!verifiedState) {
        outsideStatePolygon++;
        continue;
      }

      // Deduplication by OSM ID
      const osmId = p.id;
      if (seenIds.has(osmId)) {
        duplicateOsmIds++;
        continue;
      }
      seenIds.add(osmId);

      // Deduplication by Coordinate (within ~11 meters, 4 decimal places)
      const coordKey = `${centerType}_${lat.toFixed(4)}_${lon.toFixed(4)}`;
      if (seenCoords.has(coordKey)) {
        duplicateCoords++;
        continue;
      }
      seenCoords.add(coordKey);

      // Retain record
      typeCounts[centerType]++;
      stateDistribution[verifiedState] = (stateDistribution[verifiedState] || 0) + 1;
      const gType = feat.geometry.type;
      if (gType === 'Point' || gType === 'Polygon' || gType === 'MultiPolygon') {
        geometryCounts[gType]++;
      } else {
        geometryCounts.Other++;
      }

      const city = p.adm2_name || p.addr_city || '';
      retained.push({
        sourceId: `osm-${osmId.replace('/', '-')}`,
        name,
        centerType,
        latitude: lat,
        longitude: lon,
        city: city.trim(),
        state: verifiedState,
        phoneNumber: ''
      });

    } catch (e) {
      // skip corrupted line
    }
  }

  console.log('\n=== EXTRACTION & GEOGRAPHIC VALIDATION REPORT ===');
  console.log(`Total raw matching OSM emergency records: ${totalRawRecords}`);
  console.log(`Records missing names: ${missingNames}`);
  console.log(`Records missing coordinates: ${missingCoords}`);
  console.log(`Records outside coarse India bbox [6-38, 68-98]: ${outsideBbox}`);
  console.log(`Records outside exact India state/UT boundary polygons: ${outsideStatePolygon}`);
  console.log(`Duplicate OSM element IDs: ${duplicateOsmIds}`);
  console.log(`Duplicate coordinates (same facility within 10m): ${duplicateCoords}`);
  console.log(`TOTAL RETAINED VALID RECORDS: ${retained.length}`);

  console.log('\nBreakdown by service type:');
  console.log(`- MEDICAL (amenity=hospital): ${typeCounts.MEDICAL}`);
  console.log(`- RESPONSE_UNIT (amenity=police): ${typeCounts.RESPONSE_UNIT}`);
  console.log(`- FIRE (amenity=fire_station): ${typeCounts.FIRE}`);

  console.log('\nBreakdown by geometry primitive:');
  console.log(geometryCounts);

  console.log('\nState distribution (top 15 states):');
  const sortedStates = Object.entries(stateDistribution).sort((a, b) => b[1] - a[1]);
  for (const [st, cnt] of sortedStates.slice(0, 15)) {
    console.log(`  ${st}: ${cnt}`);
  }
  console.log(`Total states/UTs covered: ${sortedStates.length} out of 36`);

  // Write retained stats to json for inspection
  fs.writeFileSync('tools/geodata/extraction-stats.json', JSON.stringify({
    totalRawRecords,
    missingNames,
    missingCoords,
    outsideBbox,
    outsideStatePolygon,
    duplicateOsmIds,
    duplicateCoords,
    retainedCount: retained.length,
    typeCounts,
    geometryCounts,
    stateDistribution
  }, null, 2));
}

run().catch(console.error);
