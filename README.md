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
- Not implemented: Risk Engine/Risk API, Random Forest training, SOS, Notifications, Emergency Services, Offline Sync, and Geofencing.

Historical ingestion is opt-in and has no public CRUD endpoint. Set `GEOSHIELD_HISTORICAL_INGESTION_ENABLED=true` and provide both source paths through `GEOSHIELD_HISTORICAL_MORTH_SOURCE` and `GEOSHIELD_HISTORICAL_NCRB_SOURCE`. A verified local import processed 220 MoRTH records and 66 NCRB records; repeating the same source files is idempotent.

`HistoricalSafetyRecords` holds aggregate government statistics only. It does not contain individual incidents, GPS coordinates, exact timestamps, or tourist identities.

## Configuration

Copy `backend/.env.example` to an environment-specific secret store or inject its values as environment variables. Do not commit credentials or JWT signing keys.

Open decisions from the approved Architecture/SRS are marked with `TODO(architecture-open)` comments.
