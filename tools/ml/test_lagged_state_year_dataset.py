"""Independent validation of the exploratory lagged dataset."""

from __future__ import annotations

import csv
import math
from collections import Counter
from pathlib import Path


def main() -> None:
    path = Path(__file__).resolve().parents[3] / "Dataset" / "geosheild_ml_lagged_features.csv"
    with path.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))
    assert len(rows) == 142
    assert len({(r["state_ut"], r["feature_year"]) for r in rows}) == len(rows)
    assert len({(r["state_ut"], r["target_year"]) for r in rows}) == len(rows)
    assert all(int(r["target_year"]) == int(r["feature_year"]) + 1 for r in rows)
    assert all(r["state_ut"] and r["state_ut"] != "All India" for r in rows)
    assert Counter(r["target_year"] for r in rows) == {"2021": 35, "2022": 36, "2023": 36, "2024": 35}
    for row in rows:
        assert float(row["prior_accident_count"]) > 0 and float(row["target_accidents"]) > 0
        assert float(row["prior_persons_killed"]) >= 0 and float(row["target_persons_killed"]) >= 0
        assert math.isclose(float(row["prior_accident_severity"]), float(row["prior_persons_killed"]) / float(row["prior_accident_count"]) * 100)
        assert math.isclose(float(row["target_accident_severity"]), float(row["target_persons_killed"]) / float(row["target_accidents"]) * 100)
    # Explicitly separate feature columns from target-audit columns to prevent leakage in experiments.
    x_columns = {"prior_accident_count", "prior_persons_killed", "prior_accident_severity", "prior_injuries", "prior_injury_share_pct", "prior_injuries_per_lakh_population"}
    y_columns = {"target_accidents", "target_persons_killed", "target_accident_severity"}
    assert not x_columns & y_columns
    assert sum(not r["prior_injuries"] for r in rows) == 35
    assert sum(not r["prior_injury_share_pct"] for r in rows) == 36
    assert sum(not r["prior_injuries_per_lakh_population"] for r in rows) == 36
    print("Lagged dataset validation passed: 142 rows; 106 injury-feature-complete rows.")


if __name__ == "__main__":
    main()
