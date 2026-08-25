"""Build the exploratory, one-year-lagged GeoShield ML dataset.

This tool is offline-only.  It does not train a model and has no dependency on
or effect on Spring Boot production risk logic.
"""

from __future__ import annotations

import csv
import math
import sys
from collections import Counter
from pathlib import Path


ALIASES = {
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
TARGET_YEARS = (2021, 2022, 2023, 2024)


def canonical(value: str) -> str:
    return ALIASES.get(value.strip(), value.strip())


def number(value: str | None, context: str) -> float | None:
    if value is None or value.strip() in {"", "NA"}:
        return None
    try:
        result = float(value.replace(",", "").strip())
    except ValueError as error:
        raise ValueError(f"Non-numeric {value!r} in {context}") from error
    if not math.isfinite(result):
        raise ValueError(f"Non-finite {value!r} in {context}")
    return result


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_wide_history(accident_path: Path, fatalities_path: Path) -> dict[tuple[str, int], tuple[float | None, float | None]]:
    accidents = [row for row in read_csv(accident_path) if row["state_ut"] != "All India"]
    fatalities = [row for row in read_csv(fatalities_path) if row["Sl No"].strip().isdigit()]
    require(len(accidents) == len(fatalities) == 36, "Expected 36 State/UT source rows")
    accident_index = {canonical(row["state_ut"]): row for row in accidents}
    fatality_index = {canonical(row["State"]): row for row in fatalities}
    require(set(accident_index) == set(fatality_index), "Historical State/UT keys do not match")
    history = {}
    for state in sorted(accident_index):
        for year in range(2020, 2025):
            history[state, year] = (
                number(accident_index[state][f"accidents_{year}"], f"{state} accidents {year}"),
                number(fatality_index[state][f"{year} Killed"], f"{state} fatalities {year}"),
            )
    return history


def build_rows(dataset_dir: Path) -> tuple[list[dict[str, object]], list[str]]:
    base_rows = read_csv(dataset_dir / "geosheild_ml_state_year_base.csv")
    history = read_wide_history(
        dataset_dir / "road-accidents-2020-2024-state-ut.csv",
        dataset_dir / "road-accidents-2024-states-fatalities.csv",
    )
    base_index = {(canonical(row["state_ut"]), int(row["year"])): row for row in base_rows}
    require(len(base_index) == len(base_rows) == 143, "Base target data contains duplicate or unexpected rows")
    output: list[dict[str, object]] = []
    exclusions: list[str] = []
    for target_year in TARGET_YEARS:
        feature_year = target_year - 1
        for state in sorted({state for state, year in base_index if year == target_year}):
            target = base_index[state, target_year]
            prior_accidents, prior_killed = history[state, feature_year]
            if prior_accidents is None or prior_killed is None or prior_accidents <= 0:
                exclusions.append(f"{state}/{feature_year}->{target_year}: missing or non-positive prior accidents/fatalities")
                continue
            prior_annexure = base_index.get((state, feature_year))
            output.append({
                "state_ut": state,
                "feature_year": feature_year,
                "target_year": target_year,
                "prior_accident_count": int(prior_accidents),
                "prior_persons_killed": int(prior_killed),
                "prior_accident_severity": prior_killed / prior_accidents * 100,
                # Annexure 4 values are intentionally blank for feature year 2020; no source CSV exists.
                "prior_injuries": "" if prior_annexure is None else prior_annexure["injuries"],
                "prior_injury_share_pct": "" if prior_annexure is None else prior_annexure["injuries_share_pct"],
                "prior_injuries_per_lakh_population": "" if prior_annexure is None else prior_annexure["injuries_per_lakh_population"],
                "target_accidents": target["accidents"],
                "target_persons_killed": target["persons_killed"],
                "target_accident_severity": target["accident_severity"],
            })
    return output, exclusions


def validate(rows: list[dict[str, object]]) -> None:
    require(len(rows) == 142, f"Expected 142 one-year-lagged rows, found {len(rows)}")
    feature_keys = [(row["state_ut"], row["feature_year"]) for row in rows]
    target_keys = [(row["state_ut"], row["target_year"]) for row in rows]
    require(len(feature_keys) == len(set(feature_keys)), "Duplicate State/UT+feature_year")
    require(len(target_keys) == len(set(target_keys)), "Duplicate State/UT+target_year")
    require(all(row["target_year"] == row["feature_year"] + 1 for row in rows), "Invalid one-year lag")
    require(all(row["state_ut"] != "All India" and row["state_ut"] for row in rows), "Invalid State/UT output")
    for row in rows:
        prior_accidents = float(row["prior_accident_count"])
        prior_killed = float(row["prior_persons_killed"])
        target_accidents = float(row["target_accidents"])
        target_killed = float(row["target_persons_killed"])
        require(prior_accidents > 0 and target_accidents > 0, f"Non-positive accident count in {row}")
        require(prior_killed >= 0 and target_killed >= 0, f"Negative fatalities in {row}")
        require(math.isclose(float(row["prior_accident_severity"]), prior_killed / prior_accidents * 100),
                f"Prior severity mismatch: {row}")
        require(math.isclose(float(row["target_accident_severity"]), target_killed / target_accidents * 100),
                f"Target severity mismatch: {row}")


def write_rows(path: Path, rows: list[dict[str, object]]) -> None:
    columns = list(rows[0])
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)


def write_metadata(path: Path, rows: list[dict[str, object]], exclusions: list[str]) -> None:
    counts = Counter(row["target_year"] for row in rows)
    missing = {
        column: sum(not row[column] for row in rows)
        for column in ("prior_injuries", "prior_injury_share_pct", "prior_injuries_per_lakh_population")
    }
    path.write_text(f"""# GeoShield exploratory lagged ML dataset

This is an **exploratory research dataset**, not the production GeoShield risk model and not an approved production risk label.

## Target and leakage rule

`target_accident_severity = target_persons_killed / target_accidents * 100`.

Every `prior_*` column has `feature_year = target_year - 1`. The three `target_*` audit columns construct and validate y only and **must never be included in X**. The target remains a continuous severity proxy; no LOW/MEDIUM/HIGH ML thresholds are defined.

## Sources

- `road-accidents-2020-2024-state-ut.csv`: State/UT annual accident counts, 2020–2024; source metadata cites Rajya Sabha Annexure-I / MoRTH.
- `road-accidents-2024-states-fatalities.csv`: State/UT annual persons killed, 2020–2024.
- `Transport_2024_Annexure_4.csv`: State/UT injuries, injury share, and injuries per lakh for 2021–2024.
- Local MoRTH reports inspected: `1755600426_RA_2020.pdf`, `1755600426_RA_2021_Compressed.pdf`, `1755600426_RA_2022_30_Oct.pdf`, `Road-Accident-in-India-2023-Publications.pdf`, and `Road Accidents in India 2024.pdf`.

## Included feature availability

`prior_accident_count`, `prior_persons_killed`, and `prior_accident_severity` are populated for every output row. They are previous-year completed aggregate outcomes, permitted here only as lagged exploratory predictors. `prior_injuries`, `prior_injury_share_pct`, and `prior_injuries_per_lakh_population` are populated for feature years 2021–2023 where Annexure 4 values exist; missingness is preserved: {missing}.

No weather, time-of-day, urban/rural, monthly, road-environment, vehicle, or exposure feature was extracted into this CSV. The annual reports list State/UT contextual annexures, but their local source pages are image-only. Values have not been accepted without OCR and row-by-row visual validation.

## Deterministic State/UT aliases

""" + "\n".join(f"- `{source}` → `{target}`" for source, target in sorted(ALIASES.items())) + f"""

## Coverage and exclusions

- Valid lagged rows: {len(rows)}; per target year: {dict(sorted(counts.items()))}.
- Excluded: {', '.join(exclusions)}.
- `Ladakh/2020→2021` is excluded because both source measures are `NA` in 2020.
- `Lakshadweep/2024` is not a target row because 2024 accidents are zero and target severity is undefined.

## Reproducibility

Run `python tools/ml/build_lagged_state_year_dataset.py`, then `python tools/ml/test_lagged_state_year_dataset.py` from the repository root. The tools validate one-year lagging, uniqueness, source-state alignment, numeric values, target formulas, and forbidden target columns.
""", encoding="utf-8")


def main() -> int:
    dataset_dir = Path(__file__).resolve().parents[3] / "Dataset"
    rows, exclusions = build_rows(dataset_dir)
    validate(rows)
    write_rows(dataset_dir / "geosheild_ml_lagged_features.csv", rows)
    write_metadata(dataset_dir / "geosheild_ml_lagged_features.metadata.md", rows, exclusions)
    print(f"Created {len(rows)} lagged rows; exclusions: {exclusions}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError) as error:
        print(f"Lagged dataset validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
