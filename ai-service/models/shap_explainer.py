"""
SHAP-объяснения для скоринговой модели.
Возвращает топ-3 фактора, повлиявших на решение.

Запуск:
    python models/shap_explainer.py
"""

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import joblib
import numpy as np
import pandas as pd
import shap

from models.scoring_model import ALL_FEATURES, prepare_features

MODELS_DIR = os.path.dirname(__file__)

FEATURE_LABELS = {
    "region": "Регион",
    "product_type": "Вид продукции",
    "land_area": "Площадь земли",
    "requested_amount": "Запрашиваемая сумма",
    "credit_history_years": "Стаж кредитной истории",
    "prev_loans_count": "Количество прошлых кредитов",
    "prev_loans_repaid": "Погашено кредитов",
    "tax_debt": "Наличие налогового долга",
    "doc_completeness": "Полнота документов",
}


def load_models():
    reg = joblib.load(os.path.join(MODELS_DIR, "scoring_regressor.joblib"))
    encoders = joblib.load(os.path.join(MODELS_DIR, "label_encoders.joblib"))
    return reg, encoders


def get_shap_explanation(application: dict, top_n: int = 3) -> dict:
    """
    Вычисляет SHAP-значения и возвращает топ-N факторов.

    Возвращает:
        {
            "top_factors": [
                {"feature": "Полнота документов", "impact": "positive", "value": "90%", "shap_value": 0.12},
                ...
            ],
            "base_score": 62.5,
            "predicted_score": 73.7
        }
    """
    reg, encoders = load_models()

    df = pd.DataFrame([application])
    X, _ = prepare_features(df, encoders=encoders, fit=False)

    # SHAP для регрессора (скоринговый балл)
    explainer = shap.TreeExplainer(reg)
    shap_values = explainer.shap_values(X)

    base_score = float(explainer.expected_value)
    predicted_score = float(np.clip(reg.predict(X)[0], 0, 100))

    shap_row = shap_values[0]  # значения для одной заявки
    feature_names = ALL_FEATURES

    # Сортируем по абсолютному значению
    sorted_idx = np.argsort(np.abs(shap_row))[::-1]

    top_factors = []
    for i in sorted_idx[:top_n]:
        feature = feature_names[i]
        sv = float(shap_row[i])
        raw_value = application.get(feature)

        # Форматируем значение для отображения
        if raw_value is None:
            display_value = "Нет данных"
        elif feature == "doc_completeness":
            display_value = f"{int(raw_value * 100)}%"
        elif feature == "requested_amount":
            display_value = f"{int(raw_value):,} ₸".replace(",", " ")
        elif feature == "land_area":
            display_value = f"{int(raw_value)} га"
        elif feature == "tax_debt":
            display_value = "Есть долг" if raw_value else "Нет долга"
        else:
            display_value = str(raw_value)

        top_factors.append({
            "feature": FEATURE_LABELS.get(feature, feature),
            "impact": "positive" if sv > 0 else "negative",
            "value": display_value,
            "shap_value": round(sv, 3),
        })

    return {
        "top_factors": top_factors,
        "base_score": round(base_score, 1),
        "predicted_score": round(predicted_score, 1),
    }
