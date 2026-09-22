"""Export verified 2024 temporal-holdout Random Forest evaluation records.

This script reproduces the exact 2024 temporal holdout experiment from
Dataset/geosheild_ml_lagged_features.csv, verifies the resulting predictions
and actuals against tools/ml/experimental_random_forest_results.json, and
atomically exports backend/src/main/resources/data/historical_trend_evaluation_2024.json.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from sklearn.ensemble import RandomForestRegressor

EXPECTED_DATASET_SHA256 = "e2eba6a1f3276d9b345827d22ac3dfd1a524107d6539eb23a96aac211aff5167"
FEATURES = ["prior_accident_count", "prior_persons_killed", "prior_accident_severity"]
TARGET = "target_accident_severity"
FORBIDDEN_X = {"state_ut", "feature_year", "target_year", "target_accidents", "target_persons_killed", TARGET}

CONFIGURATION = {
    "n_estimators": 300,
    "max_depth": 5,
    "min_samples_split": 5,
    "min_samples_leaf": 2,
    "random_state": 42,
    "n_jobs": 1,
}


def load_and_validate_rows(source_path: Path) -> list[dict[str, str]]:
    if not source_path.is_file():
        raise FileNotFoundError(f"Source dataset not found: {source_path}")

    content = source_path.read_bytes()
    digest = hashlib.sha256(content).hexdigest()
    if digest != EXPECTED_DATASET_SHA256:
        raise ValueError(f"Dataset SHA-256 mismatch: expected {EXPECTED_DATASET_SHA256}, got {digest}")

    with source_path.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))

    if not rows:
        raise ValueError("Dataset is empty")

    required = {"state_ut", "feature_year", "target_year", TARGET, "target_accidents", "target_persons_killed"}
    if not required.issubset(rows[0]):
        raise ValueError("Dataset is missing required columns")

    for row in rows:
        if int(row["target_year"]) != int(row["feature_year"]) + 1:
            raise ValueError(f"Invalid lag in row: {row}")
        if not row["state_ut"] or row["state_ut"].strip() == "" or row["state_ut"] == "All India":
            raise ValueError(f"Invalid state_ut in row: {row}")
        if float(row["target_accidents"]) <= 0:
            raise ValueError(f"Non-positive target accidents in row: {row}")
        expected = float(row["target_persons_killed"]) / float(row["target_accidents"]) * 100
        if not math.isclose(float(row[TARGET]), expected):
            raise ValueError(f"Target formula mismatch in row: {row}")

    return rows


def select_matrices(rows: list[dict[str, str]]) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    selected = [row for row in rows if all(row[feature].strip() for feature in FEATURES)]
    if not selected:
        raise ValueError("No rows matched feature selection")

    x = np.array([[float(row[f]) for f in FEATURES] for row in selected], dtype=float)
    y = np.array([float(row[TARGET]) for row in selected], dtype=float)
    years = np.array([int(row["target_year"]) for row in selected], dtype=int)
    groups = np.array([row["state_ut"].strip() for row in selected], dtype=object)

    if np.isnan(x).any() or np.isnan(y).any():
        raise ValueError("Matrix contains NaN values")

    return x, y, years, groups


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    source_dataset = repo_root / "Dataset" / "geosheild_ml_lagged_features.csv"
    if not source_dataset.is_file():
        source_dataset = repo_root.parent / "Dataset" / "geosheild_ml_lagged_features.csv"
    results_json_path = repo_root / "tools" / "ml" / "experimental_random_forest_results.json"
    output_dir = repo_root / "backend" / "src" / "main" / "resources" / "data"
    output_json_path = output_dir / "historical_trend_evaluation_2024.json"
    temp_json_path = output_dir / "historical_trend_evaluation_2024.json.tmp"

    print("Step 1: Loading and verifying source dataset...")
    rows = load_and_validate_rows(source_dataset)
    x, y, years, groups = select_matrices(rows)

    print("Step 2: Partitioning temporal split (train <= 2023, test == 2024)...")
    train_mask = years <= 2023
    test_mask = years == 2024

    train_count = int(train_mask.sum())
    test_count = int(test_mask.sum())
    print(f"  Training rows: {train_count} (expected 107)")
    print(f"  Holdout rows:  {test_count} (expected 35)")

    if train_count != 107:
        raise ValueError(f"Expected 107 training rows, got {train_count}")
    if test_count != 35:
        raise ValueError(f"Expected 35 holdout rows, got {test_count}")

    test_states = groups[test_mask]
    if len(set(test_states)) != 35:
        raise ValueError(f"Expected 35 unique State/UT names, got {len(set(test_states))}")
    for state in test_states:
        if not state or not str(state).strip():
            raise ValueError("Encountered empty or null State/UT name in test partition")

    print("Step 3: Training deterministic RandomForestRegressor...")
    model = RandomForestRegressor(**CONFIGURATION)
    model.fit(x[train_mask], y[train_mask])

    print("Step 4: Predicting on 2024 holdout set...")
    predictions = model.predict(x[test_mask])
    actuals = y[test_mask]

    print("Step 5: Loading recorded evaluation results for numerical verification...")
    if not results_json_path.is_file():
        raise FileNotFoundError(f"Existing results file not found: {results_json_path}")

    recorded_results = json.loads(results_json_path.read_text(encoding="utf-8"))
    recorded_temporal = recorded_results["experiments"]["experiment_a"]["temporal"]["2024"]
    recorded_actuals = recorded_temporal["actual"]
    recorded_predictions = recorded_temporal["prediction"]

    if len(recorded_actuals) != 35:
        raise ValueError(f"Recorded actuals count is {len(recorded_actuals)}, expected 35")
    if len(recorded_predictions) != 35:
        raise ValueError(f"Recorded predictions count is {len(recorded_predictions)}, expected 35")

    max_actual_diff = 0.0
    max_pred_diff = 0.0
    for i in range(35):
        act_diff = abs(actuals[i] - recorded_actuals[i])
        pred_diff = abs(predictions[i] - recorded_predictions[i])
        max_actual_diff = max(max_actual_diff, act_diff)
        max_pred_diff = max(max_pred_diff, pred_diff)
        if act_diff >= 1e-5:
            raise ValueError(f"Row {i} ({test_states[i]}): actual mismatch diff={act_diff} exceeds 1e-5")
        if pred_diff >= 1e-5:
            raise ValueError(f"Row {i} ({test_states[i]}): prediction mismatch diff={pred_diff} exceeds 1e-5")

    print(f"  Numerical verification passed! Max diff actual: {max_actual_diff:.2e}, pred: {max_pred_diff:.2e}")

    print("Step 6: Constructing structured artifact with explicit State/UT pairing...")
    records = []
    for i in range(35):
        records.append({
            "geographicLevel": "STATE_UT",
            "geographicUnit": str(test_states[i]),
            "parentUnit": "India",
            "targetYear": 2024,
            "actualValue": round(float(actuals[i]), 8),
            "predictedValue": round(float(predictions[i]), 8),
            "severityMetricUnit": "fatalities_per_100_accidents",
        })

    artifact_generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    payload = {
        "targetYear": 2024,
        "advisoryReleaseVersion": "1.0.0-p0",
        "predictionSource": "2024_TEMPORAL_HOLDOUT",
        "trainingPeriod": "2021-2023",
        "trainingRows": 107,
        "evaluationRows": 35,
        "evaluationMetrics": {
            "r2": 0.9122,
            "mae": 3.879,
            "rmse": 6.1331,
            "baseline_mae": 16.4128,
            "temporalHoldout2024R2": 0.912,
            "temporalHoldout2024Mae": 3.88,
            "temporalHoldout2024Rmse": 6.13,
            "temporalHoldout2024BaselineMae": 16.41,
            "spatialGroupKFoldR2": 0.778,
            "spatialGroupKFoldMae": 5.28,
        },
        "datasetIdentifier": "geosheild_ml_lagged_features.csv",
        "datasetHashSha256": EXPECTED_DATASET_SHA256,
        "artifactGeneratedAt": artifact_generated_at,
        "trainingTimestamp": None,
        "trainingTimestampStatus": "UNAVAILABLE_NOT_RECORDED_IN_ORIGINAL_EXPERIMENT",
        "modelArtifact": None,
        "modelArtifactStatus": "UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT",
        "modelConfiguration": {
            "n_estimators": 300,
            "max_depth": 5,
            "min_samples_split": 5,
            "min_samples_leaf": 2,
            "random_state": 42,
            "n_jobs": 1,
        },
        "limitations": (
            "Retrospective 2024 model evaluation on state-level aggregate transport accident statistics. "
            "Does not capture real-time road conditions, crime, personal victimization, or tourist-specific incidents."
        ),
        "records": records,
    }

    print("Step 7: Writing artifact atomically...")
    output_dir.mkdir(parents=True, exist_ok=True)
    temp_json_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    temp_json_path.replace(output_json_path)

    print(f"Successfully exported {len(records)} verified State/UT records to {output_json_path}")
    print("\nVerified State/UT names:")
    for idx, r in enumerate(records, 1):
        print(f"  {idx:2d}. {r['geographicUnit']}: predicted={r['predictedValue']:.4f}, actual={r['actualValue']:.4f}")

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"FATAL: Exporter validation failed: {exc}", file=sys.stderr)
        sys.exit(1)
