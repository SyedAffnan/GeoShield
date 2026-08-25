"""Train/evaluate offline exploratory RandomForestRegressor experiments.

This script deliberately has no Spring Boot imports and never writes to the
backend.  The resulting artifacts are research-only and are not production
GeoShield models.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
import os
import sys
from pathlib import Path

os.environ.setdefault("MPLCONFIGDIR", str(Path(__file__).resolve().parent / ".matplotlib"))

import joblib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import GroupKFold


RANDOM_STATE = 42
CONFIGURATION = {
    "n_estimators": 300,
    "max_depth": 5,
    "min_samples_split": 5,
    "min_samples_leaf": 2,
    "random_state": RANDOM_STATE,
    "n_jobs": 1,
}
EXPERIMENTS = {
    "experiment_a": ["prior_accident_count", "prior_persons_killed", "prior_accident_severity"],
    "experiment_b": [
        "prior_accident_count", "prior_persons_killed", "prior_accident_severity",
        "prior_injuries", "prior_injury_share_pct", "prior_injuries_per_lakh_population",
    ],
}
TARGET = "target_accident_severity"
FORBIDDEN_X = {"state_ut", "feature_year", "target_year", "target_accidents", "target_persons_killed", TARGET}


def dataset_dir() -> Path:
    return Path(__file__).resolve().parents[3] / "Dataset"


def load_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise ValueError("Dataset is empty")
    required = {"state_ut", "feature_year", "target_year", TARGET, "target_accidents", "target_persons_killed"}
    if not required.issubset(rows[0]):
        raise ValueError("Dataset is missing required columns")
    if len({(r["state_ut"], r["feature_year"]) for r in rows}) != len(rows):
        raise ValueError("Duplicate State/UT+feature_year")
    if len({(r["state_ut"], r["target_year"]) for r in rows}) != len(rows):
        raise ValueError("Duplicate State/UT+target_year")
    for row in rows:
        if int(row["target_year"]) != int(row["feature_year"]) + 1:
            raise ValueError(f"Invalid lag: {row}")
        if not row["state_ut"] or row["state_ut"] == "All India":
            raise ValueError(f"Invalid state: {row}")
        if float(row["target_accidents"]) <= 0:
            raise ValueError(f"Non-positive target accidents: {row}")
        expected = float(row["target_persons_killed"]) / float(row["target_accidents"]) * 100
        if not math.isclose(float(row[TARGET]), expected):
            raise ValueError(f"Target formula mismatch: {row}")
    return rows


def select(rows: list[dict[str, str]], features: list[str]) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    if set(features) & FORBIDDEN_X:
        raise ValueError("Target or grouping column selected as X")
    selected = [row for row in rows if all(row[feature].strip() for feature in features)]
    x = np.array([[float(row[feature]) for feature in features] for row in selected], dtype=float)
    y = np.array([float(row[TARGET]) for row in selected], dtype=float)
    years = np.array([int(row["target_year"]) for row in selected])
    groups = np.array([row["state_ut"] for row in selected])
    if not len(selected) or np.isnan(x).any() or np.isnan(y).any():
        raise ValueError("Selected training matrix is empty or contains NaN")
    return x, y, years, groups


def metrics(y_true: np.ndarray, prediction: np.ndarray) -> dict[str, float]:
    return {
        "mae": float(mean_absolute_error(y_true, prediction)),
        "rmse": float(mean_squared_error(y_true, prediction) ** 0.5),
        "r2": float(r2_score(y_true, prediction)),
    }


def fit_predict(x_train: np.ndarray, y_train: np.ndarray, x_test: np.ndarray) -> np.ndarray:
    model = RandomForestRegressor(**CONFIGURATION)
    model.fit(x_train, y_train)
    return model.predict(x_test)


def temporal_evaluations(x: np.ndarray, y: np.ndarray, years: np.ndarray) -> dict[str, dict[str, object]]:
    result = {}
    for test_year in (2023, 2024):
        train = years <= test_year - 1
        test = years == test_year
        if not test.any() or not train.any():
            raise ValueError(f"No temporal split data for {test_year}")
        baseline = np.full(test.sum(), y[train].mean())
        prediction = fit_predict(x[train], y[train], x[test])
        result[str(test_year)] = {
            "train_count": int(train.sum()), "test_count": int(test.sum()),
            "baseline": metrics(y[test], baseline), "random_forest": metrics(y[test], prediction),
            "actual": y[test].tolist(), "prediction": prediction.tolist(),
        }
    return result


def grouped_evaluation(x: np.ndarray, y: np.ndarray, groups: np.ndarray) -> dict[str, object]:
    splitter = GroupKFold(n_splits=5)
    baseline_predictions = np.empty(len(y))
    rf_predictions = np.empty(len(y))
    fold_sizes = []
    for train, test in splitter.split(x, y, groups):
        baseline_predictions[test] = y[train].mean()
        rf_predictions[test] = fit_predict(x[train], y[train], x[test])
        if set(groups[train]) & set(groups[test]):
            raise ValueError("State/UT appears in both sides of grouped fold")
        fold_sizes.append({"train": int(len(train)), "validation": int(len(test)), "held_out_states": int(len(set(groups[test])))} )
    return {
        "folds": fold_sizes,
        "baseline": metrics(y, baseline_predictions),
        "random_forest": metrics(y, rf_predictions),
        "actual": y.tolist(), "prediction": rf_predictions.tolist(),
    }


def save_plots(name: str, features: list[str], grouped: dict[str, object], temporal: dict[str, dict[str, object]], importance: list[float], plots: Path) -> list[str]:
    plots.mkdir(parents=True, exist_ok=True)
    paths = []
    actual = np.array(grouped["actual"])
    prediction = np.array(grouped["prediction"])
    low, high = min(actual.min(), prediction.min()), max(actual.max(), prediction.max())
    plt.figure(figsize=(6, 5)); plt.scatter(actual, prediction, alpha=.75); plt.plot([low, high], [low, high], "k--"); plt.xlabel("Actual severity"); plt.ylabel("Grouped-CV prediction"); plt.title(f"{name}: actual vs predicted"); plt.tight_layout()
    output = plots / f"{name}_actual_vs_predicted.png"; plt.savefig(output, dpi=160); plt.close(); paths.append(str(output))
    order = np.argsort(importance)
    plt.figure(figsize=(7, 4)); plt.barh(np.array(features)[order], np.array(importance)[order]); plt.xlabel("Random Forest importance"); plt.title(f"{name}: feature importance"); plt.tight_layout()
    output = plots / f"{name}_feature_importance.png"; plt.savefig(output, dpi=160); plt.close(); paths.append(str(output))
    labels, baseline_mae, rf_mae = [], [], []
    for year, values in temporal.items():
        labels.append(year); baseline_mae.append(values["baseline"]["mae"]); rf_mae.append(values["random_forest"]["mae"])
    index = np.arange(len(labels)); width = .35
    plt.figure(figsize=(6, 4)); plt.bar(index - width / 2, baseline_mae, width, label="Fold mean baseline"); plt.bar(index + width / 2, rf_mae, width, label="Random Forest"); plt.xticks(index, labels); plt.ylabel("MAE"); plt.title(f"{name}: temporal MAE"); plt.legend(); plt.tight_layout()
    output = plots / f"{name}_temporal_mae.png"; plt.savefig(output, dpi=160); plt.close(); paths.append(str(output))
    return paths


def run_experiment(name: str, rows: list[dict[str, str]], features: list[str], digest: str, root: Path) -> dict[str, object]:
    x, y, years, groups = select(rows, features)
    temporal = temporal_evaluations(x, y, years)
    grouped = grouped_evaluation(x, y, groups)
    model = RandomForestRegressor(**CONFIGURATION).fit(x, y)
    model_path = root / "models" / f"{name}.joblib"; model_path.parent.mkdir(parents=True, exist_ok=True)
    artifact = {"model": model, "features": features, "configuration": CONFIGURATION, "random_state": RANDOM_STATE, "training_sample_count": int(len(y)), "dataset_sha256": digest, "status": "experimental_only_not_for_production"}
    joblib.dump(artifact, model_path)
    importance = model.feature_importances_.tolist()
    plots = save_plots(name, features, grouped, temporal, importance, root / "plots")
    return {"sample_count": int(len(y)), "features": features, "configuration": CONFIGURATION, "random_state": RANDOM_STATE, "dataset_sha256": digest, "temporal": temporal, "grouped": grouped, "feature_importance": dict(sorted(zip(features, importance), key=lambda item: item[1], reverse=True)), "artifact": str(model_path), "plots": plots}


def main() -> int:
    root = Path(__file__).resolve().parent
    source = dataset_dir() / "geosheild_ml_lagged_features.csv"
    contents = source.read_bytes(); digest = hashlib.sha256(contents).hexdigest()
    rows = load_rows(source)
    results = {"dataset": str(source), "dataset_sha256": digest, "target": TARGET, "target_definition": "target_persons_killed / target_accidents * 100", "status": "experimental_only_not_production", "experiments": {}}
    for name, features in EXPERIMENTS.items():
        results["experiments"][name] = run_experiment(name, rows, features, digest, root)
    output = root / "experimental_random_forest_results.json"
    output.write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(f"Wrote {output}")
    for name, experiment in results["experiments"].items():
        print(name, "samples=", experiment["sample_count"], "grouped_rf=", experiment["grouped"]["random_forest"])
    return 0


if __name__ == "__main__":
    try: raise SystemExit(main())
    except (OSError, ValueError, KeyError) as error:
        print(f"Experimental training failed: {error}", file=sys.stderr); raise SystemExit(1)
