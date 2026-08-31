"""Загрузка утверждённой скоринговой модели и расчёт результата заявки."""

from __future__ import annotations

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import hashlib
from datetime import datetime, timezone
import joblib
import numpy as np
import pandas as pd
from sklearn.preprocessing import OrdinalEncoder

CATEGORICAL_FEATURES = ["region", "product_type"]
NUMERIC_FEATURES = [
    "land_area",
    "requested_amount",
    "credit_history_years",
    "prev_loans_count",
    "prev_loans_repaid",
    "tax_debt",
    "doc_completeness",
]
ALL_FEATURES = CATEGORICAL_FEATURES + NUMERIC_FEATURES

MODELS_DIR = os.path.dirname(__file__)
MODEL_FILES = [
    "scoring_classifier.joblib",
    "scoring_regressor.joblib",
    "label_encoders.joblib",
]


def prepare_features(df: pd.DataFrame, encoders=None, fit: bool = True):
    """Подготовка признаков: кодирование категорий через OrdinalEncoder."""
    X = df[ALL_FEATURES].copy()

    if fit:
        enc = OrdinalEncoder(handle_unknown="use_encoded_value", unknown_value=-1)
        X[CATEGORICAL_FEATURES] = enc.fit_transform(X[CATEGORICAL_FEATURES].astype(str))
        encoders = {"ordinal": enc}
    else:
        X[CATEGORICAL_FEATURES] = encoders["ordinal"].transform(X[CATEGORICAL_FEATURES].astype(str))

    for feature in NUMERIC_FEATURES:
        X[feature] = pd.to_numeric(X[feature], errors="coerce")

    return X, encoders


_cache: dict = {}


def load_models_cached():
    """Загружает модели один раз и кэширует в памяти."""
    if not _cache:
        _cache["clf"] = joblib.load(os.path.join(MODELS_DIR, "scoring_classifier.joblib"))
        _cache["reg"] = joblib.load(os.path.join(MODELS_DIR, "scoring_regressor.joblib"))
        _cache["enc"] = joblib.load(os.path.join(MODELS_DIR, "label_encoders.joblib"))
    return _cache["clf"], _cache["reg"], _cache["enc"]


def score_application(application: dict, model_clf, model_reg, encoders: dict) -> dict:
    """
    Скоринг одной заявки.

    application: dict с полями из ALL_FEATURES
    Возвращает: score (0–100), risk_level, approved, probability
    """
    df = pd.DataFrame([application])
    X, _ = prepare_features(df, encoders=encoders, fit=False)

    score = float(np.clip(model_reg.predict(X)[0], 0, 100))
    prob = float(model_clf.predict_proba(X)[0][1])
    approved = bool(model_clf.predict(X)[0])

    if score >= 70:
        risk_level = "low"
    elif score >= 45:
        risk_level = "medium"
    else:
        risk_level = "high"

    recommended_amount = None
    if approved and "requested_amount" in application:
        recommended_amount = round(application["requested_amount"] * min(1.0, score / 100 * 1.3))

    return {
        "score": round(score, 1),
        "risk_level": risk_level,
        "approved": approved,
        "approval_probability": round(prob, 3),
        "recommended_amount": recommended_amount,
        "data_availability": data_availability(application),
    }


def data_availability(application: dict) -> dict:
    return {
        feature: application.get(feature) is not None
        for feature in NUMERIC_FEATURES + CATEGORICAL_FEATURES
    }


def input_indicators(application: dict, source: str = "JAVA_BACKEND") -> list[dict]:
    received_at = datetime.now(timezone.utc).isoformat()
    indicators = []
    for feature in ALL_FEATURES:
        value = application.get(feature)
        indicators.append({
            "field": feature,
            "value": value,
            "source": source,
            "receivedAt": received_at,
            "verificationStatus": "UNVERIFIED" if value is not None else "MISSING",
        })
    return indicators


def model_metadata() -> dict:
    return {
        "modelName": os.getenv("AI_MODEL_NAME", "kendala-xgboost-scorecard"),
        "version": os.getenv("AI_MODEL_VERSION", "demo-unverified"),
        "trainingDate": os.getenv("AI_MODEL_TRAINING_DATE"),
        "featureList": ALL_FEATURES,
        "datasetVersionHash": os.getenv("AI_DATASET_VERSION_HASH"),
        "evaluationMetrics": configured_metrics(),
        "artifactHash": artifact_hash(),
        "verificationStatus": os.getenv("AI_MODEL_VERIFICATION_STATUS", "DEMO"),
    }


def configured_metrics() -> dict:
    raw = os.getenv("AI_MODEL_EVALUATION_METRICS", "").strip()
    if not raw:
        return {}
    return {"configured": raw}


def artifact_hash() -> str | None:
    digest = hashlib.sha256()
    try:
        for file_name in MODEL_FILES:
            with open(os.path.join(MODELS_DIR, file_name), "rb") as handle:
                for chunk in iter(lambda: handle.read(65536), b""):
                    digest.update(chunk)
        return digest.hexdigest()
    except FileNotFoundError:
        return None
