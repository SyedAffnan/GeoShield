"""Validation checks for the derived GeoShield ML dataset; not a production test."""

from __future__ import annotations

import csv
import math
import sys
from collections import Counter
from pathlib import Path


def main() -> int:
    dataset = Path(__file__).resolve().parents[3] / "Dataset" / "geosheild_ml_state_year_base.csv"
    with dataset.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))

    required_columns = {
        "state_ut", "year", "accidents", "persons_killed", "accident_severity",
        "injuries", "injuries_share_pct", "injuries_per_lakh_population",
    }
    assert rows and set(rows[0]) == required_columns
    assert len(rows) == 143
    keys = [(row["state_ut"], row["year"]) for row in rows]
    assert len(keys) == len(set(keys))
    assert all(row["state_ut"].strip() and row["state_ut"] != "All India" for row in rows)
    assert Counter(row["year"] for row in rows) == {"2021": 36, "2022": 36, "2023": 36, "2024": 35}
    assert Counter(row["state_ut"] for row in rows)["Lakshadweep"] == 3
    assert all(float(row["accidents"]) > 0 for row in rows)
    assert all(float(row["persons_killed"]) >= 0 for row in rows)
    assert all(
        math.isclose(float(row["accident_severity"]), float(row["persons_killed"]) / float(row["accidents"]) * 100)
        for row in rows
    )
    # These are target-construction columns and are intentionally absent from X.
    model_features = {"state_ut", "year", "injuries", "injuries_share_pct", "injuries_per_lakh_population"}
    assert not model_features.intersection({"persons_killed", "accidents", "accident_severity"})
    missing_optional = [row for row in rows if not row["injuries_share_pct"] or not row["injuries_per_lakh_population"]]
    assert [(row["state_ut"], row["year"]) for row in missing_optional] == [("Ladakh", "2021")]
    print("Derived dataset validation passed: 143 target-valid rows; 142 feature-complete rows.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, KeyError, ValueError) as error:
        print(f"Derived dataset validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
