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
- IMPLEMENTED AND VERIFIED: Weather risk factor — the tourist's current coordinates are used to fetch a **real current weather observation** from Open-Meteo, whose WMO weather code is mapped to a published MoRTH weather condition and normalized by that condition's published fatality severity. The observation is real-time and location-specific; the risk relationship behind it is a MoRTH **national aggregate** and is not tourist-specific. No weather observation is ever synthesized.
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


## Weather risk source

The weather factor is the only risk factor that combines **two independent real sources**, and the README keeps them separate on purpose:

1. **What the weather currently is** — a live observation from a third-party provider, for the tourist's own coordinates.
2. **How much that weather condition matters** — a published government fatality severity, from the MoRTH report.

Neither source alone can produce the feature, and neither is substituted for the other. If the observation is missing the factor reports **unavailable**; it never assumes a default condition, reuses a previous observation, or falls back to a seasonal average.

### 1. The observation (real-time)

- **Provider**: [Open-Meteo](https://open-meteo.com/), selected for Architecture v3.2 §35 / §61 item 1, which left "Weather provider selection" explicitly open with no vendor assumed. Open-Meteo needs **no API key and no account**, so there is no weather credential to store, inject, or rotate — `WeatherProviderProperties` deliberately declares no key/secret/token/password component, and a test asserts that.
- **Endpoint**: `GET https://api.open-meteo.com/v1/forecast`
- **Parameters**: `latitude`, `longitude`, `current=weather_code`, `timezone=UTC`. Only the current weather code is requested — no forecast, no temperature, no wind, no history.
- **Location source**: the authenticated tourist's stored current location, read through the existing `LocationService` boundary. The risk module does not read the location repository.
- **Privacy**: coordinates are **rounded to 2 decimal places (~1 km) before transmission**, which is still finer than Open-Meteo's own model grid — a live response to `latitude=12.97&longitude=77.59` reports back `12.970123, 77.56364`, so nothing is gained by sending the full-precision fix. Coordinates are **never logged**, and they are never placed into the risk feature, its explanation, or its provenance. Provider failures are logged by exception class name only, because an exception message can echo the request URI and therefore the coordinates.
- **Failure handling**: connect and read timeouts are configured and mandatory (the properties record rejects a zero, negative, or missing timeout), so an unresponsive provider cannot hang a risk request. Any transport error, non-2xx status, unparseable body, missing `weather_code`, or uninterpretable observation time yields an explicit unavailable result.
- **Configuration** (`geoshield.weather`, all overridable by environment variable, none of them a secret):

| Property | Environment variable | Default |
| --- | --- | --- |
| `enabled` | `GEOSHIELD_WEATHER_ENABLED` | `true` |
| `base-url` | `GEOSHIELD_WEATHER_BASE_URL` | `https://api.open-meteo.com` |
| `connect-timeout` | `GEOSHIELD_WEATHER_CONNECT_TIMEOUT` | `PT2S` |
| `read-timeout` | `GEOSHIELD_WEATHER_READ_TIMEOUT` | `PT3S` |

Setting `enabled=false` makes the factor report unavailable and makes **no outbound request at all**; it does not switch to a synthesized condition. This was verified at runtime.

### 2. The severity (published, historical)

The risk relationship reads a bundled classpath resource, `backend/src/main/resources/risk/morth-2024-weather-condition.csv`, transcribed verbatim from the source report.

- **Source**: Ministry of Road Transport & Highways (MoRTH), Transport Research Wing, *Road Accidents in India 2024*.
- **Table**: Table 3.8, "Number of road accidents, persons killed and injured by weather condition", **2024 columns**, printed **page 50** (physical PDF page 83).
- **Source year**: 2024.
- **Geographic scope**: **national aggregate for India**. It is **not** State/UT-specific and **not** tourist-specific — two tourists in different States observing the same weather condition receive the same severity.

| MoRTH condition | Accidents 2024 | Persons killed 2024 | Share of accidents (computed) | Killed per 100 accidents (derived) | Normalized risk |
| --- | --- | --- | --- | --- | --- |
| Sunny / clear | 3,72,387 | 1,28,513 | 76.4% | 34.51060322 | 76.01868939 |
| Rainy | 35,284 | 12,665 | 7.2% | 35.89445641 | 79.06699039 |
| Foggy & misty | 33,955 | 15,115 | 7.0% | 44.51479900 | 98.05556447 |
| Hail / sleet | 4,310 | 1,919 | 0.9% | 44.52436195 | 98.07662938 |
| Others | 41,771 | 18,963 | 8.6% | 45.39752460 | 100.00000000 |
| **Total** | **4,87,707** | **1,77,175** | | | |

Only the two count columns are transcribed. The "share of accidents" column above is **computed** from them for explanation, not transcribed, and it is **not** used in the score. Both published totals reconcile exactly against the five rows, which the test suite asserts as a transcription check.

**Normalization** — `persons killed per 100 accidents for the observed condition / maximum same-measure published condition × 100`, at scale 8 with `HALF_UP` rounding. Architecture v3.2, the SRS, and the SDD specify only that a feature must land in the fusion range; they do not prescribe how MoRTH weather data maps onto it, so this rule is **architecture-open**, and it is the smallest reproducible mapping consistent with the relative-maximum convention already approved for the historical and time-of-day factors. To avoid compounded rounding, each value is evaluated as a single division: `killed_c × accidents_max × 100 / (accidents_c × killed_max)`.

**MoRTH does not publish a 0–100 weather risk score.** The normalized column above is derived by GeoShield from MoRTH's published counts, and every risk explanation the API produces states that explicitly.

**Why fatality rate and not accident share.** Sunny/clear weather accounts for **76.4% of all accidents** — by far the largest count in the table — simply because most travel happens in clear weather. Feeding that share into the same relative-maximum rule would score clear weather at 100 (maximum danger) and hail/sleet at about 1.2, which inverts reality. **Persons killed per 100 accidents** instead conditions on an accident having already occurred, so the unpublished exposure denominator cancels out and the surviving ordering is a genuine severity ordering: clear 34.51 < rainy 35.89 < foggy 44.51 < hail 44.52 < others 45.40. The test suite asserts this directly, including a test named for the fact that clear weather must not be ranked most dangerous despite its largest accident share.

### 3. WMO code → MoRTH condition mapping

Open-Meteo reports weather as a WMO present-weather code. MoRTH publishes exactly five weather conditions. The mapping between them is fixed, documented, and deliberately incomplete:

| MoRTH condition | WMO codes | Justification |
| --- | --- | --- |
| Sunny / clear | 0, 1, 2, 3 | Clear sky, mainly clear, partly cloudy, overcast — dry with unimpaired visibility. Overcast is included because MoRTH publishes no separate "cloudy" condition and cloud alone is neither precipitation nor a visibility obstruction. |
| Foggy & misty | 45, 48 | Fog and depositing rime fog — MoRTH's own visibility-obstruction condition. |
| Rainy | 51, 53, 55, 61, 63, 65, 80, 81, 82, 95 | Drizzle (all intensities), rain (all intensities), rain showers (all intensities), and thunderstorm without hail — all liquid precipitation. |
| Hail / sleet | 56, 57, 66, 67, 71, 73, 75, 77, 85, 86, 96, 99 | Freezing drizzle, freezing rain, snowfall, snow grains, snow showers, and thunderstorm with hail — all frozen or freezing precipitation. |
| Others | *(none)* | Never assigned. See below. |

28 WMO codes are mapped. **Every other code is deliberately unmapped and produces an explicit unavailable result** rather than an approximation — for example WMO 4 (visibility reduced by smoke) has no MoRTH counterpart, so no category is assumed for it.

Two mapping decisions are worth stating plainly, because both were judgement calls:

- **MoRTH's "Others" row is never used as a fallback.** It is tempting, since it is the one bucket for conditions the other four do not cover — but its composition is unpublished, and it also happens to carry the *highest* fatality rate in the table (45.40). Routing an unrecognized code there would silently assert the worst published severity on a guess. Unmapped codes therefore report unavailable instead. A consequence, accepted rather than engineered away: because "Others" holds the maximum, **no observable condition ever normalizes to exactly 100** — the highest reachable value is hail/sleet at 98.07662938.
- **Snow is mapped to "Hail / sleet."** MoRTH publishes no snow condition, and hail/sleet is its only frozen-precipitation row, so snow is grouped there rather than sent to "Others" or invented as a sixth category. This is recorded as a limitation below, not presented as a published MoRTH classification.

### Availability

The factor is **available only when all four hold**: a current location exists, the provider returns a current weather observation, the returned WMO code is in the documented mapping, and the MoRTH severity table loaded successfully. Otherwise it reports **unavailable with a specific reason** and contributes exactly zero. The weight stays at the approved **0.20** in every case; it is never rebalanced to compensate for an unavailable factor. If the severity table cannot be loaded, no outbound request is made at all, since the observation could not be used regardless.

Provenance carried on every weather feature: provider name, observation time, raw WMO code, mapped MoRTH condition label, the published accident and fatality counts behind it, the derived killed-per-100-accidents figure, the MoRTH source and year, the normalization rule, and a statement that MoRTH publishes no 0–100 weather score. As with the historical and time-of-day factors, this provenance lives on the risk feature at the service boundary; the `GET /api/v1/risk` response carries the baseline engine's short per-factor explanation, which is the existing contract for all seven factors.

Known limitations:

- The severity is a **national single-year (2024) aggregate**. Only the observation is location-specific; the risk weighting attached to it is not, and it is not tourist-specific.
- The severity describes **all reported road accidents**, not tourists and not crime.
- **Snow is scored as hail/sleet**, because MoRTH publishes no snow condition (see above).
- **Resolution is limited to five conditions**, so light and heavy rain receive the same value — MoRTH publishes no intensity breakdown.
- Weather conditions **outside the 28 mapped WMO codes report unavailable**, including smoke, dust, and sandstorm conditions that MoRTH does not itemize.
- Annexure 25 does publish State/UT-wise weather-condition data, but those annexure pages of the source PDF are image-only scans with no extractable text layer (the same blocker as Annexure 42), so those values could not be transcribed without guessing. Recorded as an open item rather than worked around.
- The observation depends on a third party being reachable. That is a real external dependency, handled by explicit unavailability rather than by a fallback value. **The test suite never depends on it**: every test stubs the transport, so `mvn clean test` passes offline.


Copy `backend/.env.example` to an environment-specific secret store or inject its values as environment variables. Do not commit credentials or JWT signing keys.

Open decisions from the approved Architecture/SRS are marked with `TODO(architecture-open)` comments.
