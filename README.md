# GeoShield

GeoShield is an AI-powered tourist safety and emergency-response system.

This repository contains the approved project skeleton only. Business logic is intentionally not implemented.

## Projects

- `backend/` — Spring Boot 3.x, Java 21 modular monolith.
- `mobile/` — Flutter 3.x Android-first mobile client.

## Backend modules

`identity`, `location`, `risk`, `sos`, `incident`, `notification`, `emergencyservices`, and `admin` are the only backend module boundaries. Modules communicate through service interfaces.

## Configuration

Copy `backend/.env.example` to an environment-specific secret store or inject its values as environment variables. Do not commit credentials or JWT signing keys.

Open decisions from the approved Architecture/SRS are marked with `TODO(architecture-open)` comments.
