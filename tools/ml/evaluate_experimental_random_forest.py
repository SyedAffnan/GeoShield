"""Print reproducible, saved evaluation results without retraining."""
from __future__ import annotations
import json
from pathlib import Path

results = Path(__file__).resolve().parent / "experimental_random_forest_results.json"
data = json.loads(results.read_text(encoding="utf-8"))
for name, experiment in data["experiments"].items():
    print(f"{name}: samples={experiment['sample_count']}")
    for year, outcome in experiment["temporal"].items():
        print(f"  temporal {year}: baseline={outcome['baseline']}; rf={outcome['random_forest']}")
    print(f"  grouped: baseline={experiment['grouped']['baseline']}; rf={experiment['grouped']['random_forest']}")
