# Experimental Random Forest regression

This directory holds offline research tooling only. It never imports, modifies, or serves the Spring Boot risk engine.

Run, after generating `Dataset/geosheild_ml_lagged_features.csv`:

```powershell
tools\ml\.venv\Scripts\python.exe tools\ml\train_experimental_random_forest.py
tools\ml\.venv\Scripts\python.exe tools\ml\evaluate_experimental_random_forest.py
```

Experiment A uses three prior-year historical severity fields (142 rows). Experiment B adds the three prior-year injury fields where published (106 rows). Both use `RandomForestRegressor` with the fixed conservative configuration in the training script. State/UT is used only as a `GroupKFold` group, never as a predictor. The saved models are explicitly experimental and must not be integrated with GeoShield production scoring.
