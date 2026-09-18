"""
CSV validation script for india-emergency-service-centers.csv
Validates records, center types, coordinate boundaries, deduplication, missing fields, and state distribution.
"""
import csv
import sys
from collections import Counter

CSV_PATH = r"D:\Final year project\GeoSheild\backend\src\main\resources\emergencyservices\india-emergency-service-centers.csv"

def run_validation():
    encodings = ['utf-8', 'utf-8-sig', 'latin-1']
    file_obj = None
    used_encoding = None

    for enc in encodings:
        try:
            f = open(CSV_PATH, 'r', encoding=enc)
            for _ in range(10):
                f.readline()
            f.seek(0)
            file_obj = f
            used_encoding = enc
            break
        except UnicodeDecodeError:
            continue

    if not file_obj:
        print(f"Error: Could not open file with encodings {encodings}")
        sys.exit(1)

    def line_generator(f):
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith('#'):
                continue
            yield line

    reader = csv.DictReader(line_generator(file_obj))

    total_records = 0
    center_types = Counter()
    invalid_coords = []
    seen_ids = set()
    duplicate_source_ids = Counter()
    dup_ids = set()
    empty_names = []
    empty_states = []
    empty_cities = []
    first_5 = []
    last_5 = []
    states = set()

    for idx, row in enumerate(reader, 1):
        total_records += 1

        if total_records <= 5:
            first_5.append((idx, dict(row)))

        if len(last_5) >= 5:
            last_5.pop(0)
        last_5.append((idx, dict(row)))

        c_type = row.get('center_type', '').strip()
        center_types[c_type] += 1

        sid = row.get('source_id', '').strip()
        if sid:
            if sid in seen_ids:
                dup_ids.add(sid)
                duplicate_source_ids[sid] += 1
            else:
                seen_ids.add(sid)
                duplicate_source_ids[sid] = 1

        name = row.get('name', '').strip()
        if not name:
            empty_names.append((idx, sid))

        state = row.get('state', '').strip()
        if not state:
            empty_states.append((idx, sid))
        else:
            states.add(state)

        city = row.get('city', '').strip()
        if not city:
            empty_cities.append((idx, sid))

        lat_str = row.get('latitude', '').strip()
        lon_str = row.get('longitude', '').strip()
        try:
            lat = float(lat_str)
            lon = float(lon_str)
            if not (6.0 <= lat <= 37.0 and 68.0 <= lon <= 98.0):
                invalid_coords.append((idx, sid, lat, lon, f"Out of India bounds [6.0-37.0, 68.0-98.0]"))
        except ValueError:
            invalid_coords.append((idx, sid, lat_str, lon_str, "Non-numeric coordinate"))

    file_obj.close()

    print("==================================================")
    print("CSV VALIDATION REPORT")
    print(f"File: {CSV_PATH}")
    print(f"Encoding: {used_encoding}")
    print("==================================================")
    print(f"Total data records (excluding comments/header): {total_records}")
    print("\nCenter types breakdown:")
    for ct, count in center_types.most_common():
        print(f"  - {ct}: {count}")

    print(f"\nDuplicate source_ids count: {len(dup_ids)}")
    if dup_ids:
        print(f"Sample duplicate source_ids (up to 10): {list(dup_ids)[:10]}")

    print(f"\nEmpty/missing name records count: {len(empty_names)}")
    if empty_names:
        print(f"Sample missing name rows: {empty_names[:5]}")

    print(f"\nEmpty/missing state records count: {len(empty_states)}")
    if empty_states:
        print(f"Sample missing state rows: {empty_states[:5]}")

    print(f"\nEmpty/missing city records count: {len(empty_cities)}")
    if empty_cities:
        print(f"Sample missing city rows (total {len(empty_cities)}): {empty_cities[:5]}")

    print(f"\nInvalid coordinates count: {len(invalid_coords)}")
    if invalid_coords:
        print(f"Sample invalid coordinates (up to 10): {invalid_coords[:10]}")

    print(f"\nUnique states/UTs count: {len(states)}")
    print("Unique states/UTs list (sorted):")
    for s in sorted(states):
        print(f"  - {s}")

    print("\nFirst 5 data records:")
    for idx, row in first_5:
        print(f"  Row {idx}: {row}")

    print("\nLast 5 data records:")
    for idx, row in last_5:
        print(f"  Row {idx}: {row}")

if __name__ == '__main__':
    run_validation()
