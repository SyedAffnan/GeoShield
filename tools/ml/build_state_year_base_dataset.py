"""Build and validate the non-production GeoShield State/UT-year ML base dataset.

The script is deliberately independent from the Spring Boot application.  It
does not modify source datasets or production risk logic.  It validates every
source total before writing the derived CSV.
"""

from __future__ import annotations

import csv
import math
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from statistics import mean, median, quantiles


YEARS = (2020, 2021, 2022, 2023, 2024)
ANNEXURE_YEARS = (2021, 2022, 2023, 2024)
CANONICAL_ALIASES = {
    "Andaman & Nicobar Islands": "Andaman and Nicobar Islands",
    "Andaman & Nicobar Island": "Andaman and Nicobar Islands",
    "Arunanchal Pradesh": "Arunachal Pradesh",
    "Dadra & Nagar Haveli": "Dadra and Nagar Haveli and Daman and Diu",
    "Dadra & Nagar Haveli and Daman & Diu": "Dadra and Nagar Haveli and Daman and Diu",
    "Dadra & Nagar Havelli and Daman & Diu": "Dadra and Nagar Haveli and Daman and Diu",
    "J & K": "Jammu and Kashmir",
    "J & K #": "Jammu and Kashmir",
    "Jammu & Kashmir": "Jammu and Kashmir",
}


@dataclass(frozen=True)
class SourcePaths:
    accidents: Path
    fatalities: Path
    annexure_4: Path
    boundaries: Path
    output_csv: Path
    metadata: Path


def canonical_state(value: str) -> str:
    return CANONICAL_ALIASES.get(value.strip(), value.strip())


def parse_number(value: str | None, context: str) -> float | None:
    if value is None or value.strip() in {"", "NA"}:
        return None
    try:
        number = float(value.replace(",", "").strip())
    except ValueError as error:
        raise ValueError(f"Non-numeric value {value!r} in {context}") from error
    if not math.isfinite(number):
        raise ValueError(f"Non-finite number {value!r} in {context}")
    return number


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_accidents(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    require(
        [*rows[0]] == [
            "state_ut", "accidents_2020", "accidents_2021", "accidents_2022",
            "accidents_2023", "accidents_2024", "source_organization",
            "source_document", "source_url", "notes",
        ],
        "Unexpected accidents CSV columns",
    )
    states = [row for row in rows if row["state_ut"].strip() != "All India"]
    total = next((row for row in rows if row["state_ut"].strip() == "All India"), None)
    require(total is not None, "Accidents CSV has no All India row")
    require(len(states) == 36, f"Expected 36 accident State/UT rows, found {len(states)}")
    require(not any(not row["state_ut"].strip() for row in rows), "Blank accidents row found")
    duplicates = [state for state, count in Counter(canonical_state(row["state_ut"]) for row in states).items() if count > 1]
    require(not duplicates, f"Duplicate accident State/UT records: {duplicates}")
    for year in YEARS:
        state_total = sum(parse_number(row[f"accidents_{year}"], f"{row['state_ut']} {year}") or 0 for row in states)
        published_total = parse_number(total[f"accidents_{year}"], f"All India {year}")
        require(state_total == published_total, f"Accident total does not reconcile for {year}: {state_total} != {published_total}")
    return states


def validate_fatalities(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    expected_columns = [
        "Sl No", "State", "2020 Killed", "2021 Killed", "2022 Killed", "2023 Killed", "2024 Killed",
        "% change from 2023 to 2024", "2020 Ranking", "2021 Ranking", "2022 Ranking", "2023 Ranking", "2024 Ranking",
    ]
    require([*rows[0]] == expected_columns, "Unexpected fatalities CSV columns")
    states = [row for row in rows if row["Sl No"].strip().isdigit()]
    total = next((row for row in rows if row["State"].strip() == "All India"), None)
    require(total is not None, "Fatalities CSV has no All India row")
    require(len(states) == 36, f"Expected 36 fatalities State/UT rows, found {len(states)}")
    duplicates = [state for state, count in Counter(canonical_state(row["State"]) for row in states).items() if count > 1]
    require(not duplicates, f"Duplicate fatalities State/UT records: {duplicates}")
    for year in YEARS:
        state_total = sum(parse_number(row[f"{year} Killed"], f"{row['State']} {year}") or 0 for row in states)
        published_total = parse_number(total[f"{year} Killed"], f"All India {year}")
        require(state_total == published_total, f"Fatality total does not reconcile for {year}: {state_total} != {published_total}")
    return states


def annexure_columns() -> dict[int, tuple[str, str, str]]:
    return {
        year: (
            f"State / UT - wise Total Number of Persons Injured in Road Accidents during - {year}" + (" - Number" if year == 2024 else ""),
            f"Share of States / UTs in Total Number of Persons Injured in Road Accidents - {year}",
            f"Total Number of Persons Injured in Road Accidents Per Lakh Population - {year}",
        )
        for year in ANNEXURE_YEARS
    }


def validate_annexure(rows: list[dict[str, str]]) -> dict[str, dict[str, str]]:
    states = [row for row in rows if row["State/UT"].strip() != "Total"]
    require(len(states) == 36, f"Expected 36 Annexure 4 State/UT rows, found {len(states)}")
    duplicates = [state for state, count in Counter(canonical_state(row["State/UT"]) for row in states).items() if count > 1]
    require(not duplicates, f"Duplicate Annexure 4 State/UT records: {duplicates}")
    for year, columns in annexure_columns().items():
        for column in columns:
            require(column in rows[0], f"Annexure 4 column missing: {column}")
        injury_column = columns[0]
        for row in states:
            require(parse_number(row[injury_column], f"Annexure 4 {row['State/UT']} {year}") is not None,
                    f"Missing Annexure 4 injury count: {row['State/UT']} {year}")
    return {canonical_state(row["State/UT"]): row for row in states}


def validate_geojson_names(path: Path, expected_states: set[str]) -> None:
    import json
    with path.open(encoding="utf-8") as source:
        features = json.load(source).get("features", [])
    names = {canonical_state(feature["properties"]["state_name"]) for feature in features}
    require(len(features) == 36, f"Expected 36 GeoJSON features, found {len(features)}")
    require(names == expected_states, f"GeoJSON State/UT mismatch: missing={sorted(expected_states - names)}, extra={sorted(names - expected_states)}")


def build_rows(accident_rows: list[dict[str, str]], fatality_rows: list[dict[str, str]], annexure: dict[str, dict[str, str]]) -> tuple[list[dict[str, object]], list[str]]:
    accidents = {canonical_state(row["state_ut"]): row for row in accident_rows}
    fatalities = {canonical_state(row["State"]): row for row in fatality_rows}
    states = set(accidents)
    require(states == set(fatalities) == set(annexure), "State/UT key sets do not match after normalization")
    missing: list[str] = []
    output: list[dict[str, object]] = []
    for state in sorted(states):
        for year in ANNEXURE_YEARS:
            accidents_value = parse_number(accidents[state][f"accidents_{year}"], f"{state} accidents {year}")
            killed_value = parse_number(fatalities[state][f"{year} Killed"], f"{state} fatalities {year}")
            if accidents_value is None or killed_value is None:
                missing.append(f"{state}/{year}: missing accidents or fatalities")
                continue
            if accidents_value <= 0:
                missing.append(f"{state}/{year}: accidents must be > 0 (observed {accidents_value:g})")
                continue
            injury_column, share_column, per_lakh_column = annexure_columns()[year]
            output.append({
                "state_ut": state,
                "year": year,
                "accidents": int(accidents_value),
                "persons_killed": int(killed_value),
                "accident_severity": killed_value / accidents_value * 100,
                "injuries": int(parse_number(annexure[state][injury_column], f"{state} injuries {year}")),
                "injuries_share_pct": parse_number(annexure[state][share_column], f"{state} injury share {year}"),
                "injuries_per_lakh_population": parse_number(annexure[state][per_lakh_column], f"{state} injuries/lakh {year}"),
            })
    return output, missing


def validate_output(rows: list[dict[str, object]]) -> None:
    require(len(rows) == 143, f"Expected 143 valid 2021-2024 target samples, found {len(rows)}")
    keys = [(row["state_ut"], row["year"]) for row in rows]
    require(len(keys) == len(set(keys)), "Derived data contains duplicate State/UT-year rows")
    require(all(row["state_ut"] and row["state_ut"] != "All India" for row in rows), "Invalid State/UT output row")
    for row in rows:
        require(row["accidents"] > 0, f"Non-positive accidents for {row}")
        require(row["persons_killed"] >= 0, f"Negative fatalities for {row}")
        require(math.isclose(row["accident_severity"], row["persons_killed"] / row["accidents"] * 100),
                f"Severity formula mismatch for {row}")
        require(row["accident_severity"] >= 0, f"Negative severity for {row}")


def write_csv(rows: list[dict[str, object]], path: Path) -> None:
    fieldnames = ["state_ut", "year", "accidents", "persons_killed", "accident_severity", "injuries", "injuries_share_pct", "injuries_per_lakh_population"]
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def write_metadata(path: Path, rows: list[dict[str, object]], excluded: list[str], paths: SourcePaths) -> None:
    severities = [float(row["accident_severity"]) for row in rows]
    q1, _, q3 = quantiles(severities, n=4, method="inclusive")
    iqr = q3 - q1
    upper_fence = q3 + 1.5 * iqr
    outliers = [row for row in rows if float(row["accident_severity"]) > upper_fence]
    state_counts = Counter(row["state_ut"] for row in rows)
    year_counts = Counter(int(row["year"]) for row in rows)
    content = f"""# GeoShield State/UT-Year ML Base Dataset\n\n+## Provenance\n\n+- Accident counts: `{paths.accidents.name}` (2020–2024; Rajya Sabha Annexure-I citation retained in the source file).\n+- Fatalities: `{paths.fatalities.name}` (2020–2024).\n+- Injury features: `{paths.annexure_4.name}` (2021–2024).\n+- Boundary name validation: `{paths.boundaries.name}` (36 features).\n\n+## Deterministic join key\n\n+`state_ut` is normalized only for joins. The aliases are:\n\n+| Source spelling | Canonical join key |\n+| --- | --- |\n+""" + "\n".join(f"| {source} | {target} |" for source, target in sorted(CANONICAL_ALIASES.items())) + f"""\n\n+## Target\n\n+`accident_severity = persons_killed / accidents * 100`\n\n+Values are retained at full floating-point precision in the CSV; rounding is presentation-only. `persons_killed`, `accidents`, and `accident_severity` are target construction fields and **must not be used in X**.\n\n+## Validated feature availability\n\n+For every year 2021–2024, Annexure 4 supplies `injuries`, `injuries_share_pct`, and `injuries_per_lakh_population`. It supplies `injuries_per_10000_vehicles` and `injuries_per_10000_km_roads` for 2022 only, and an injury rank for 2024 only; these incomplete-year columns are excluded.\n\n+Recommended X: `state_ut` (encoded), `year`, `injuries`, `injuries_share_pct`, `injuries_per_lakh_population`.\n+Recommended y: `accident_severity`.\n\n+## Exclusions and limitations\n\n+- `Ladakh/2020` is absent from both source measures; it is outside this 2021–2024 feature-complete dataset.\n+- `Lakshadweep/2024` has zero accidents and zero fatalities. Its severity is undefined, so it is excluded rather than imputed.\n+- All India rows, source blank rows, and source footnote rows are excluded.\n+- The resulting panel has {len(rows)} valid samples: {dict(sorted(year_counts.items()))}; samples per State/UT range from {min(state_counts.values())} to {max(state_counts.values())}.\n+- Severity range: {min(severities):.12g}–{max(severities):.12g}; mean {mean(severities):.12g}; median {median(severities):.12g}.\n+- IQR outlier fence: {upper_fence:.12g}; flagged rows: {', '.join(f"{r['state_ut']}/{r['year']} ({float(r['accident_severity']):.2f})" for r in outliers) or 'none'}. These rows are retained pending domain review.\n\n+## Build and validation\n\n+Run from the repository root:\n\n+```powershell\n+python tools/ml/build_state_year_base_dataset.py\n+```\n\n+The script validates source schemas, source totals, State/UT key sets, duplicate keys, formula correctness, zero denominators, and the expected 143-row feature-complete output before it writes files. It has no production runtime dependency.\n"""
    content = content.replace("\n+", "\n").replace(
        "For every year 2021–2024, Annexure 4 supplies `injuries`, `injuries_share_pct`, and `injuries_per_lakh_population`. It supplies `injuries_per_10000_vehicles` and `injuries_per_10000_km_roads` for 2022 only, and an injury rank for 2024 only; these incomplete-year columns are excluded.",
        "For every year 2021–2024, Annexure 4 supplies `injuries`. `injuries_share_pct` and `injuries_per_lakh_population` are available for all rows except `Ladakh/2021`, where the published values are `NA`. It supplies `injuries_per_10000_vehicles` and `injuries_per_10000_km_roads` for 2022 only, and an injury rank for 2024 only; these incomplete-year columns are excluded.",
    ).replace(
        f"- The resulting panel has {len(rows)} valid samples: {dict(sorted(year_counts.items()))}; samples per State/UT range from {min(state_counts.values())} to {max(state_counts.values())}.",
        f"- The resulting panel has {len(rows)} valid target samples: {dict(sorted(year_counts.items()))}; samples per State/UT range from {min(state_counts.values())} to {max(state_counts.values())}. The full recommended X feature set is complete for {sum(row['injuries_share_pct'] is not None and row['injuries_per_lakh_population'] is not None for row in rows)} rows; `Ladakh/2021` must be omitted from that feature-complete training subset unless a later, documented imputation policy is approved.",
    ).replace(
        "expected 143-row feature-complete output",
        "expected 143-row target-valid output",
    )
    path.write_text(content, encoding="utf-8")


def main() -> int:
    dataset_dir = Path(__file__).resolve().parents[3] / "Dataset"
    paths = SourcePaths(
        accidents=dataset_dir / "road-accidents-2020-2024-state-ut.csv",
        fatalities=dataset_dir / "road-accidents-2024-states-fatalities.csv",
        annexure_4=dataset_dir / "Transport_2024_Annexure_4.csv",
        boundaries=dataset_dir / "state_NWIC.GeoJSON",
        output_csv=dataset_dir / "geosheild_ml_state_year_base.csv",
        metadata=dataset_dir / "geosheild_ml_state_year_base.metadata.md",
    )
    accident_rows = validate_accidents(read_csv(paths.accidents))
    fatality_rows = validate_fatalities(read_csv(paths.fatalities))
    annexure = validate_annexure(read_csv(paths.annexure_4))
    validate_geojson_names(paths.boundaries, {canonical_state(row["state_ut"]) for row in accident_rows})
    output_rows, excluded = build_rows(accident_rows, fatality_rows, annexure)
    validate_output(output_rows)
    write_csv(output_rows, paths.output_csv)
    write_metadata(paths.metadata, output_rows, excluded, paths)
    print(f"Validated source totals and created {len(output_rows)} rows: {paths.output_csv}")
    print("Excluded combinations:")
    for exclusion in excluded:
        print(f"- {exclusion}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration) as error:
        print(f"Validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
