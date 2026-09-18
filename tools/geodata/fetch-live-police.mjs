import fs from 'fs';

const ep = 'https://overpass.private.coffee/api/interpreter';

async function fetchLivePolice() {
  const quads = [
    { name: 'South-West (6-20N, 68-82E)', bbox: '6.0,68.0,20.0,82.0' },
    { name: 'South-East (6-20N, 82-98E)', bbox: '6.0,82.0,20.0,98.0' },
    { name: 'North-West (20-38N, 68-82E)', bbox: '20.0,68.0,38.0,82.0' },
    { name: 'North-East (20-38N, 82-98E)', bbox: '20.0,82.0,38.0,98.0' }
  ];

  const allPolice = [];
  for (const qd of quads) {
    console.log(`Fetching live Overpass police in ${qd.name}...`);
    const q = `
      [out:json][timeout:60];
      (
        nwr["amenity"="police"]["name"](${qd.bbox});
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
        console.log(`Returned ${d.elements?.length} elements in ${qd.name}`);
        if (d.elements) allPolice.push(...d.elements);
      } else {
        console.log(`Status ${res.status} in ${qd.name}`);
      }
    } catch (e) {
      console.log(`Error in ${qd.name}:`, e.message);
    }
    await new Promise(r => setTimeout(r, 1500));
  }

  console.log(`Total live police stations fetched: ${allPolice.length}`);
  fs.writeFileSync('tools/geodata/downloads/overpass-police.json', JSON.stringify(allPolice, null, 2));
  console.log('Saved to tools/geodata/downloads/overpass-police.json');
}

fetchLivePolice().catch(console.error);
