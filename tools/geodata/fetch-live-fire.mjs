import fs from 'fs';

const ep = 'https://overpass.private.coffee/api/interpreter';

async function fetchLiveFire() {
  console.log('Fetching live Overpass fire stations to check for additional stations...');
  const q = `
    [out:json][timeout:60];
    (
      nwr["amenity"="fire_station"]["name"](6.0,68.0,38.0,98.0);
    );
    out center;
  `;
  try {
    const res = await fetch(ep, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'User-Agent': 'GeoShield-Research/1.0'
      },
      body: 'data=' + encodeURIComponent(q)
    });
    if (res.ok) {
      const d = await res.json();
      console.log(`Live fire stations returned: ${d.elements?.length}`);
      fs.writeFileSync('tools/geodata/downloads/overpass-fire.json', JSON.stringify(d.elements, null, 2));
      console.log('Saved to tools/geodata/downloads/overpass-fire.json');
    } else {
      console.log('Overpass fire fetch status:', res.status);
    }
  } catch (e) {
    console.log('Error:', e.message);
  }
}

fetchLiveFire().catch(console.error);
