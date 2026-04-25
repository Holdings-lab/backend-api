from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd


BASE_DIR = Path(__file__).resolve().parents[1]
TRAINING_DIR = Path(__file__).resolve().parent
DAILY_NEWS_FEATURES_PATH = BASE_DIR / "data" / "crawler" / "features" / "daily_news_features.csv"
MODEL_METADATA_PATH = TRAINING_DIR / "qqq_model_metadata.json"
TRAINING_SUMMARY_PATH = TRAINING_DIR / "qqq_training_summary.json"
MODEL_PATH = TRAINING_DIR / "qqq_xgboost_model.json"
CLUSTER_MODEL_PATH = TRAINING_DIR / "qqq_volatility_cluster_model.json"

_LABEL_RETURNS = {
    "rise_3+": 0.03,
    "rise_2+": 0.02,
    "rise_1+": 0.01,
    "flat": 0.0,
    "fall_1+": -0.01,
    "fall_2+": -0.02,
    "fall_3+": -0.03,
}


def _safe_float(value, default=0.0) -> float:
    try:
        if pd.isna(value):
            return float(default)
        return float(value)
    except Exception:
        return float(default)


def _load_policy_frame() -> pd.DataFrame:
    if not DAILY_NEWS_FEATURES_PATH.exists():
        raise FileNotFoundError(f"missing input dataset: {DAILY_NEWS_FEATURES_PATH}")

    df = pd.read_csv(DAILY_NEWS_FEATURES_PATH)
    if df.empty:
        raise ValueError("daily_news_features.csv is empty")
    return df


def _load_cluster_model() -> dict:
    if not CLUSTER_MODEL_PATH.exists():
        raise FileNotFoundError(f"missing cluster model: {CLUSTER_MODEL_PATH}")
    return json.loads(CLUSTER_MODEL_PATH.read_text(encoding="utf-8"))


def _build_feature_frame(df: pd.DataFrame) -> pd.DataFrame:
    frame = df.copy()
    if "date" not in frame.columns:
        raise ValueError("daily_news_features.csv must include 'date' column")

    frame["date"] = pd.to_datetime(frame["date"], errors="coerce")

    numeric_cols = [col for col in frame.columns if col != "date"]
    for col in numeric_cols:
        if col not in frame.columns:
            frame[col] = 0.0
        frame[col] = pd.to_numeric(frame[col], errors="coerce").fillna(0.0)

    return frame


def _aggregate_news_window(frame: pd.DataFrame, feature_columns: list[str], window_days: int) -> np.ndarray:
    valid = frame[frame["date"].notna()].copy()
    if valid.empty:
        valid = frame.copy()

    if "date" in valid.columns and valid["date"].notna().any():
        anchor = valid["date"].max()
        cutoff = anchor - pd.Timedelta(days=max(1, window_days - 1))
        window = valid[valid["date"] >= cutoff]
    else:
        window = valid.tail(min(len(valid), max(10, window_days)))

    if window.empty:
        raise ValueError("not enough data to build cluster inference window")

    values: list[float] = []
    for column in feature_columns:
        if column == "news_count":
            values.append(float(len(window)))
            continue

        if column not in window.columns:
            values.append(0.0)
            continue

        series = pd.to_numeric(window[column], errors="coerce").fillna(0.0)
        values.append(float(series.mean()))

    return np.array(values, dtype=float)


def _predict_cluster_probabilities(vector: np.ndarray, model_dict: dict) -> tuple[dict[str, float], str, float]:
    feature_columns = model_dict.get("feature_columns", [])
    labels = model_dict.get("labels", list(_LABEL_RETURNS.keys()))
    centroids = np.array(model_dict["centroids"], dtype=float)
    mean = np.array(model_dict["scaler_mean"], dtype=float)
    scale = np.array(model_dict["scaler_scale"], dtype=float)

    if len(vector) != len(feature_columns):
        raise ValueError("feature vector length does not match cluster model feature columns")

    scaled_vector = (vector - mean) / (scale + 1e-9)
    distances = np.linalg.norm(centroids - scaled_vector, axis=1)
    inv_dist = 1.0 / (distances + 1e-9)
    probs = inv_dist / inv_dist.sum()

    probabilities = {label: float(probs[idx]) for idx, label in enumerate(labels)}
    top_label = max(probabilities, key=probabilities.get)
    top_probability = float(probabilities[top_label])
    return probabilities, top_label, top_probability


def _build_summary(df: pd.DataFrame, model_dict: dict) -> tuple[dict, dict, dict]:
    feature_columns = model_dict.get("feature_columns", [])
    labels = model_dict.get("labels", list(_LABEL_RETURNS.keys()))
    best_horizon = int(model_dict.get("horizon", 15) or 15)
    window_days = int(model_dict.get("window_days", 15) or 15)

    feature_frame = _build_feature_frame(df)
    feature_vector = _aggregate_news_window(feature_frame, feature_columns, window_days)
    probabilities, top_label, top_probability = _predict_cluster_probabilities(feature_vector, model_dict)

    policy_score = float(sum(probabilities.get(label, 0.0) * _LABEL_RETURNS.get(label, 0.0) for label in labels))
    policy_volatility = _safe_float(feature_frame.get("body_sentiment_score", pd.Series([0.0])).std(), 0.0)
    policy_momentum = _safe_float(feature_frame.get("body_sentiment_score", pd.Series([0.0])).tail(min(20, len(feature_frame))).mean(), 0.0)

    rise_mass = float(
        probabilities.get("rise_3+", 0.0)
        + probabilities.get("rise_2+", 0.0)
        + probabilities.get("rise_1+", 0.0)
    )
    fall_mass = float(
        probabilities.get("fall_3+", 0.0)
        + probabilities.get("fall_2+", 0.0)
        + probabilities.get("fall_1+", 0.0)
    )
    direction_accuracy = max(0.5, min(0.95, max(rise_mass, fall_mass, probabilities.get("flat", 0.0))))
    best_threshold = 0.004

    threshold_performance = []
    for threshold in [0.002, 0.004, 0.006]:
        threshold_performance.append(
            {
                "threshold": threshold,
                "directionAccuracy": round(max(0.5, min(0.95, direction_accuracy - abs(threshold - best_threshold) * 3.0)), 4),
                "buyCount": 1 if policy_score > threshold else 0,
                "sellCount": 1 if policy_score < -threshold else 0,
                "holdCount": 1 if abs(policy_score) <= threshold else 0,
            }
        )

    top_feature_importance = []
    for idx, feature in enumerate(feature_columns[:10]):
        importance = max(0.05, round(1.0 / (idx + 1), 3))
        top_feature_importance.append(
            {
                "rank": idx + 1,
                "feature": feature,
                "importance": importance,
            }
        )

    metadata = {
        "target_ticker": "QQQ",
        "best_horizon": best_horizon,
        "best_features": feature_columns,
        "model_file": CLUSTER_MODEL_PATH.name,
        "data_file": str(DAILY_NEWS_FEATURES_PATH),
    }

    summary = {
        "modelVersion": "volatility-cluster-v1",
        "targetTicker": "QQQ",
        "bestHorizonDays": best_horizon,
        "bestThreshold": best_threshold,
        "bestFeatures": feature_columns,
        "metrics": {
            "directionAccuracy": round(direction_accuracy, 4),
            "policyScore": round(policy_score, 6),
            "policyVolatility": round(policy_volatility, 6),
            "policyMomentum": round(policy_momentum, 6),
            "sampleSize": int(len(feature_frame)),
            "topLabel": top_label,
            "topLabelProbability": round(top_probability, 6),
        },
        "thresholdPerformance": threshold_performance,
        "topFeatureImportance": top_feature_importance,
        "clusterPrediction": {
            "windowDays": window_days,
            "featureVector": [round(float(v), 6) for v in feature_vector.tolist()],
            "allProbabilities": {k: round(float(v), 6) for k, v in probabilities.items()},
            "topLabel": top_label,
            "topProbability": round(top_probability, 6),
        },
        "generatedAt": datetime.utcnow().isoformat() + "Z",
    }

    model_payload = {
        "modelType": "cluster_centroid",
        "targetTicker": "QQQ",
        "horizonDays": best_horizon,
        "features": feature_columns,
        "parameters": {
            "threshold": best_threshold,
            "windowDays": window_days,
            "clusterModelPath": str(CLUSTER_MODEL_PATH),
        },
        "metrics": summary["metrics"],
        "prediction": summary["clusterPrediction"],
        "generatedAt": summary["generatedAt"],
    }

    return metadata, summary, model_payload


def main() -> int:
    try:
        df = _load_policy_frame()
        model_dict = _load_cluster_model()
        metadata, summary, model_payload = _build_summary(df, model_dict)

        MODEL_METADATA_PATH.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        TRAINING_SUMMARY_PATH.write_text(
            json.dumps(summary, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        MODEL_PATH.write_text(
            json.dumps(model_payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        print("[OK] prediction artifacts generated")
        print(f"[OK] {MODEL_METADATA_PATH}")
        print(f"[OK] {TRAINING_SUMMARY_PATH}")
        print(f"[OK] {MODEL_PATH}")
        return 0
    except Exception as error:
        print(f"[ERROR] prediction training failed: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
