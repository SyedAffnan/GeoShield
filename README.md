# GeoShield

GeoShield is an AI-powered tourist safety and emergency-response system.

This repository contains the Spring Boot backend and Flutter client project structure.

## Projects

- `backend/` — Spring Boot 3.x, Java 21 modular monolith.
- `mobile/` — Flutter 3.x Android-first mobile client.

## Backend modules

`identity`, `location`, `historicaldata`, `risk`, `sos`, `incident`, `notification`, `emergencyservices`, and `admin` are the backend module boundaries. Modules communicate through service interfaces.

## Implementation status

- Implemented: Identity/Auth, Tourist Profile, Emergency Contacts, Device Tokens, and Location.
- Implemented: Historical Data Processing and `HistoricalSafetyRecords` persistence. The batch pipeline normalizes the approved MoRTH Road Accidents in India 2024 Annexure-4 CSV and NCRB Crime in India 2023 Table 13A.2 CSV into one aggregate-statistics table.
- IMPLEMENTED AND VERIFIED: Incident reporting, authenticated owner-scoped incident reads, request idempotency, and SHA-256 integrity verification.
- IMPLEMENTED: Baseline Contextual Risk Engine — deterministic weighted scoring, explainability, safety recommendations, and `RiskScores` audit persistence. It does not synthesize unavailable contextual data.
- IMPLEMENTED AND VERIFIED: Offline GPS → State/UT resolution — deterministic point-in-polygon lookup against a bundled derived boundary resource, feeding the historical-incident risk factor. No external geocoding service and no personal filesystem path is used.
- IMPLEMENTED AND VERIFIED: Time-of-day risk factor — the current time selects its MoRTH 3-hour interval and that interval's published national accident count is normalized into the risk feature. The distribution is a **national aggregate**, not State/UT-specific and not tourist-specific.
- IMPLEMENTED AND VERIFIED: Risk API — `GET /api/v1/risk` returns the authenticated tourist's current baseline risk using their stored current location.
- Not implemented: Random Forest training, AI/ML training, Contextual Risk Fusion, SOS, Notifications, Emergency Services, Offline Sync, and Geofencing. Geographic resolution is at State/UT granularity only; city, district, and sub-district resolution are not implemented.

Historical ingestion is opt-in and has no public CRUD endpoint. Set `GEOSHIELD_HISTORICAL_INGESTION_ENABLED=true` and provide both source paths through `GEOSHIELD_HISTORICAL_MORTH_SOURCE` and `GEOSHIELD_HISTORICAL_NCRB_SOURCE`. A verified local import processed 220 MoRTH records and 66 NCRB records; repeating the same source files is idempotent.

`HistoricalSafetyRecords` holds aggregate government statistics only. It does not contain individual incidents, GPS coordinates, exact timestamps, or tourist identities.

## State/UT boundary resource

GPS coordinates are resolved to a State/UT by deterministic point-in-polygon lookup (ray casting with MultiPolygon and polygon-hole handling) against a bundled classpath resource, `backend/src/main/resources/geo/india-state-ut-boundaries.geojson`. No bounding boxes, centroids, coordinate-range rules, or external geocoding services are used, and a coordinate that no polygon contains returns an explicit unresolved result rather than a guess.

This bundled file is a **derived artifact**, not the original dataset. It is produced offline from the Survey of India (SOI) / NWIC `state_NWIC` administrative-boundary GeoJSON by the auditable, dependency-free scripts under `tools/geodata/` (a dev-only tool tree that is not a build or runtime dependency). The derivation:

- **Reprojects** every coordinate from the source CRS **EPSG:7755** (WGS 84 / India NSF LCC, projected metres) to **EPSG:4326** (WGS84 longitude/latitude). Because EPSG:7755's base geographic CRS is already WGS84, this is a pure inverse Lambert Conformal Conic map projection with no datum shift; the inverse is cross-validated against `proj4` to within ~1e-7 m over ~15,000 test points.
- **Simplifies** each polygon with Ramer–Douglas–Peucker at a ~11 m tolerance and **rounds** coordinates to 6 decimal places (~0.11 m), reducing the resource from ~47 MiB / ~923k vertices to ~8 MiB / ~393k vertices while preserving State/UT-level resolution.
- **Keeps only** the `state_name` and `stcode` properties.
- Applies **four explicit, documented State/UT name normalizations** so the resolved name matches the MoRTH `geographicUnit` character-for-character (no fuzzy matching and no global `&` replacement): `Andaman & Nicobar Island` → `Andaman and Nicobar Islands`; `Arunanchal Pradesh` → `Arunachal Pradesh`; `Dadra & Nagar Havelli and Daman & Diu` → `Dadra and Nagar Haveli and Daman and Diu`; `Jammu & Kashmir` → `Jammu and Kashmir`. The other 32 names are already identical. The script fails fast if a source name is unrecognized or a mapping key never matches.

The source dataset is Survey of India / NWIC intellectual property and is **not** copied into this repository; only the derived, reprojected, simplified resource is bundled. To regenerate it, see `tools/geodata/README` and run the preprocessing script against a local copy of the source GeoJSON, passing that path as an argument (no path is hard-coded).

Known limitation: each State/UT is simplified independently, so points within roughly the simplification tolerance of a shared border may fall in a small gap (returned as unresolved) or an overlap (resolved deterministically to the first matching polygon in dataset order). This is well within consumer-GPS error and does not affect interior resolution.

## Time-of-day risk resource

The time-of-day factor reads a bundled classpath resource, `backend/src/main/resources/risk/morth-2024-time-of-day.csv`, transcribed verbatim from the source report.

- **Source**: Ministry of Road Transport & Highways (MoRTH), Transport Research Wing, *Road Accidents in India 2024*.
- **Table**: Table 7.3, "Number of road accidents by time interval of day", **2024 column**, printed **page 98** (physical PDF page 142). Chart 7.4 on the same page is a visual restatement of that column's percentage shares, and section 7.6 independently confirms the two largest shares in prose.
- **Source year**: 2024.
- **Geographic scope**: **national aggregate for India**. It is **not** State/UT-specific and it is **not** tourist-specific. Architecture v3.2 §29 defines this feature (`timeIntervalRiskShare`) as "MoRTH, national % share by 3-hour band", and §50.1 records that MoRTH publishes time-of-day only as 3-hour interval bands aggregated nationally per year, so no finer granularity is claimed than the table provides.
- **Time reference**: MoRTH times of occurrence are Indian local time. Bands are therefore resolved in `Asia/Kolkata`, not UTC. In production the real system clock is used; tests inject a fixed `Clock` and never change system time.

| Interval (IST) | Accidents 2024 | Published share | Day/Night | Normalized risk |
| --- | --- | --- | --- | --- |
| 00:00 to 03:00 hrs | 25,001 | 5.1% | Night | 24.29711265 |
| 03:00 to 06:00 hrs | 23,398 | 4.8% | Night | 22.73924410 |
| 06:00 to 09:00 hrs | 49,395 | 10.1% | Day | 48.00431499 |
| 09:00 to 12:00 hrs | 68,287 | 14.0% | Day | 66.36442268 |
| 12:00 to 15:00 hrs | 71,386 | 14.6% | Day | 69.37617229 |
| 15:00 to 18:00 hrs | 85,010 | 17.4% | Day | 82.61659718 |
| 18:00 to 21:00 hrs | 1,02,897 | 21.1% | Night | 100.00000000 |
| 21:00 to 24:00 hrs | 56,215 | 11.5% | Night | 54.63230221 |

**Normalization** — `interval accident count / maximum interval accident count × 100`, at scale 8 with `HALF_UP` rounding, so the busiest published interval maps to exactly 100. Architecture v3.2, the SRS, and the SDD specify only that a feature must land in the fusion range; they do not prescribe how a `*RiskShare` maps onto it, so this rule is **architecture-open** and is chosen because it is character-for-character the relative-maximum convention already approved and implemented for the historical factor. It is computed from the published counts rather than the published percentages because the printed percentages are rounded to one decimal place; the two are algebraically the same quantity, since the common total cancels: `(c_i / T) / (c_max / T) = c_i / c_max`.

Table 7.3's separate **"Unknown Time"** row (6,118 accidents, 1.3%) is deliberately excluded: it is a data-completeness bucket rather than a clock interval, so no current instant can fall into it. Excluding it cannot alter the denominator, because it is not the maximum. The eight interval rows sum to 4,81,589, and adding "Unknown Time" reproduces the published Total 24 hrs of 4,87,707 — a reconciliation asserted by the test suite.

Known limitations:

- The distribution describes **all reported road accidents nationally**, not tourists, not crime, and not any specific State/UT. Two users in different States at the same moment receive the same time-of-day value.
- It is a single-year (2024) aggregate with no seasonal, weekday/weekend, or road-category breakdown, because Table 7.3 provides none.
- Resolution is limited to 3-hour granularity, so the value is constant within each interval and steps at interval boundaries.
- Annexure 42 does publish State-wise time-of-interval data, but the annexure pages of the source PDF are image-only scans with no extractable text layer, so those values could not be transcribed without guessing. Using them would also have widened the feature's scope beyond the architecture's stated national aggregation. Both are recorded as open items rather than worked around.
- If the resource is missing or fails validation, the factor reports **unavailable** with a reason instead of failing the request or synthesizing a value.


## Configuration

Copy `backend/.env.example` to an environment-specific secret store or inject its values as environment variables. Do not commit credentials or JWT signing keys.

Open decisions from the approved Architecture/SRS are marked with `TODO(architecture-open)` comments.
