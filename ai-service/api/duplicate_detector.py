"""
Детектор дублей заявок.

Проверяет:
1. Точный дубль — тот же ИИН/БИН + активная заявка
2. Мягкий дубль — схожие параметры (регион, продукция, сумма ±20%)
3. Аномалии — нереалистичные значения (сумма/га слишком высокая и т.д.)
"""

from __future__ import annotations
from dataclasses import dataclass, field


@dataclass
class ApplicationRecord:
    application_id: str
    iin_bin: str
    region: str
    product_type: str
    land_area: float
    requested_amount: float
    status: str  # draft / submitted / review / approved / rejected


@dataclass
class DuplicateCheckResult:
    is_duplicate: bool
    duplicate_type: str | None          # "exact" | "soft" | None
    matched_application_id: str | None
    flags: list[str] = field(default_factory=list)
    anomalies: list[str] = field(default_factory=list)


ACTIVE_STATUSES = {
    "draft",
    "submitted",
    "review",
    "in_review",
    "additional_documents_requested",
    "DRAFT",
    "SUBMITTED",
    "IN_REVIEW",
    "ADDITIONAL_DOCUMENTS_REQUESTED",
}
# Максимально разумная сумма на гектар (примерная норма — 200 000 ₸/га)
MAX_AMOUNT_PER_HA = 500_000


def check_duplicates(
    new_app: ApplicationRecord,
    existing: list[ApplicationRecord],
) -> DuplicateCheckResult:
    """
    Проверяет новую заявку на дубли и аномалии.
    """
    flags = []
    anomalies = []

    # 1. Точный дубль: тот же ИИН/БИН и активная заявка
    for app in existing:
        if app.iin_bin == new_app.iin_bin and app.status in ACTIVE_STATUSES:
            return DuplicateCheckResult(
                is_duplicate=True,
                duplicate_type="exact",
                matched_application_id=app.application_id,
                flags=[f"Активная заявка {app.application_id} с тем же ИИН/БИН уже существует"],
                anomalies=anomalies,
            )

    # 2. Мягкий дубль: одинаковый регион + продукция + сумма ±20%
    for app in existing:
        if app.status not in ACTIVE_STATUSES:
            continue
        if app.region == new_app.region and app.product_type == new_app.product_type:
            amount_diff = abs(app.requested_amount - new_app.requested_amount) / max(app.requested_amount, 1)
            if amount_diff <= 0.2:
                flags.append(
                    f"Похожая заявка {app.application_id} в том же регионе "
                    f"({app.region}), та же продукция ({app.product_type}), "
                    f"сумма отличается на {amount_diff:.0%}"
                )

    # 3. Аномалии
    if new_app.land_area > 0:
        amount_per_ha = new_app.requested_amount / new_app.land_area
        if amount_per_ha > MAX_AMOUNT_PER_HA:
            anomalies.append(
                f"Высокая сумма на гектар: "
                f"{amount_per_ha:,.0f} ₸/га (норма до {MAX_AMOUNT_PER_HA:,} ₸/га)"
            )

    if new_app.requested_amount > 100_000_000:
        anomalies.append("Очень большая сумма кредита (> 100 млн ₸) — требует дополнительной проверки")

    if new_app.land_area > 5000:
        anomalies.append("Очень большая площадь (> 5000 га) — возможна ошибка ввода")

    is_soft_dup = len(flags) > 0

    return DuplicateCheckResult(
        is_duplicate=is_soft_dup,
        duplicate_type="soft" if is_soft_dup else None,
        matched_application_id=None,
        flags=flags,
        anomalies=anomalies,
    )
