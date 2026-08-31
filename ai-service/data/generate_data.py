"""
Генерация синтетических данных для обучения скоринговой модели.
Параметры заявки: кредитная история, налоги, регион, вид продукции, площадь.
"""

import numpy as np
import pandas as pd

REGIONS = [
    "Акмолинская область",
    "Костанайская область",
    "Туркестанская область",
    "Алматинская область",
    "Северо-Казахстанская область",
]

# Коэффициент риска по региону (чем меньше — тем лучше для кредита)
REGION_RISK = {
    "Акмолинская область": 0.15,
    "Костанайская область": 0.18,
    "Северо-Казахстанская область": 0.20,
    "Алматинская область": 0.25,
    "Туркестанская область": 0.30,
}

PRODUCT_TYPES = [
    "Пшеница",
    "Ячмень",
    "Масличные культуры",
    "Овощи",
    "Молочная продукция",
]

# Средняя доходность по виду продукции (выше — меньше риск)
PRODUCT_YIELD = {
    "Пшеница": 0.70,
    "Ячмень": 0.65,
    "Масличные культуры": 0.80,
    "Молочная продукция": 0.85,
    "Овощи": 0.60,
}


def generate_dataset(n_samples: int = 1000, random_state: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(random_state)

    regions = rng.choice(REGIONS, size=n_samples)
    product_types = rng.choice(PRODUCT_TYPES, size=n_samples)

    # Числовые признаки
    land_area = rng.integers(10, 1000, size=n_samples).astype(float)
    requested_amount = rng.integers(500_000, 50_000_000, size=n_samples).astype(float)
    credit_history_years = rng.integers(0, 20, size=n_samples).astype(float)
    prev_loans_count = rng.integers(0, 10, size=n_samples).astype(float)
    prev_loans_repaid = np.minimum(
        prev_loans_count,
        rng.integers(0, 11, size=n_samples).astype(float),
    )
    tax_debt = rng.choice([0, 0, 0, 1], size=n_samples).astype(float)  # 75% без долгов
    doc_completeness = rng.uniform(0.5, 1.0, size=n_samples)  # доля поданных документов

    # Расчёт целевого скорингового балла (0–100)
    region_risk = np.array([REGION_RISK[r] for r in regions])
    product_yield = np.array([PRODUCT_YIELD[p] for p in product_types])

    repay_rate = np.where(prev_loans_count > 0, prev_loans_repaid / prev_loans_count, 0.5)
    amount_to_land_ratio = requested_amount / (land_area + 1)
    amount_to_land_norm = np.clip(1 - amount_to_land_ratio / 100_000, 0, 1)

    score_raw = (
        0.25 * repay_rate
        + 0.20 * (credit_history_years / 20)
        + 0.15 * (1 - tax_debt)
        + 0.15 * doc_completeness
        + 0.15 * product_yield
        + 0.10 * amount_to_land_norm
        - 0.10 * region_risk
    )

    # Добавляем шум и масштабируем до 0–100
    noise = rng.normal(0, 0.03, size=n_samples)
    score = np.clip((score_raw + noise) * 100, 0, 100).round(1)

    # Метка: одобрить (1) если скоринг >= 50
    approved = (score >= 50).astype(int)

    df = pd.DataFrame({
        "region": regions,
        "product_type": product_types,
        "land_area": land_area,
        "requested_amount": requested_amount,
        "credit_history_years": credit_history_years,
        "prev_loans_count": prev_loans_count,
        "prev_loans_repaid": prev_loans_repaid,
        "tax_debt": tax_debt,
        "doc_completeness": doc_completeness,
        "score": score,
        "approved": approved,
    })

    return df


if __name__ == "__main__":
    df = generate_dataset(n_samples=2000)
    output_path = "data/applications_dataset.csv"
    df.to_csv(output_path, index=False)
    print(f"Dataset saved: {output_path}")
    print(f"Shape: {df.shape}")
    print(f"\nApproval rate: {df['approved'].mean():.1%}")
    print(f"Score distribution:\n{df['score'].describe().round(1)}")
    print(f"\nSample:\n{df.head(3)}")
