"""
Генерация текстового заключения для комиссии.

Поддерживаются два провайдера:
  * gemini — Google Gemini (GEMINI_API_KEY), модель по умолчанию gemini-3.6-flash;
  * openai — OpenAI (OPENAI_API_KEY), модель gpt-4o-mini.

Провайдер выбирается переменной AI_LLM_PROVIDER (gemini|openai|auto).
В режиме auto берётся тот, для которого задан ключ; Gemini имеет приоритет.

Использование:
    from api.llm_conclusion import generate_conclusion
    text = generate_conclusion(application_data, scoring_result, language="ru")
"""

import json
import os
import urllib.error
import urllib.request

from dotenv import load_dotenv
from api.request_context import correlation_id

load_dotenv()

try:
    from openai import OpenAI
    OPENAI_IMPORT_ERROR = None
    OPENAI_AVAILABLE = True
except ImportError as exc:
    OpenAI = None
    OPENAI_IMPORT_ERROR = exc
    OPENAI_AVAILABLE = False

RISK_LABELS = {
    "ru": {"low": "низкий", "medium": "средний", "high": "высокий"},
    "kz": {"low": "төмен", "medium": "орташа", "high": "жоғары"},
    "en": {"low": "low", "medium": "medium", "high": "high"},
}

IMPACT_LABELS = {
    "ru": {"positive": "положительно", "negative": "отрицательно"},
    "kz": {"positive": "оң", "negative": "теріс"},
    "en": {"positive": "positive", "negative": "negative"},
}

# Финальная инструкция для модели на языке документа.
LANGUAGE_INSTRUCTIONS = {
    "ru": (
        "Напиши краткое профессиональное заключение для членов комиссии на русском языке "
        "(3–5 предложений). Укажи ключевые риски или сильные стороны заявки. "
        "Не используй markdown-форматирование."
    ),
    "kz": (
        "Комиссия мүшелеріне арналған қысқа кәсіби қорытындыны қазақ тілінде жаз "
        "(3–5 сөйлем). Өтінімнің негізгі тәуекелдері мен күшті жақтарын көрсет. "
        "Markdown пішімдеуін қолданба."
    ),
    "en": (
        "Write a short professional conclusion for the credit committee in English "
        "(3–5 sentences). Highlight the key risks or strengths of the application. "
        "Do not use markdown formatting."
    ),
}

DEFAULT_LANGUAGE = "ru"
SUPPORTED_LANGUAGES = tuple(LANGUAGE_INSTRUCTIONS)

OPENAI_TIMEOUT_SECONDS = min(
    max(float(os.getenv("OPENAI_TIMEOUT_SECONDS", "30")), 1),
    120,
)
OPENAI_MAX_RETRIES = min(
    max(int(os.getenv("OPENAI_MAX_RETRIES", "2")), 0),
    5,
)
GEMINI_TIMEOUT_SECONDS = min(
    max(float(os.getenv("GEMINI_TIMEOUT_SECONDS", "30")), 1),
    120,
)
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.6-flash")
GEMINI_MAX_OUTPUT_TOKENS = min(max(int(os.getenv("GEMINI_MAX_OUTPUT_TOKENS", "2000")), 256), 8192)
GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"


class LlmUnavailableError(RuntimeError):
    pass


class LlmConfigurationError(LlmUnavailableError):
    pass


def llm_enabled() -> bool:
    return os.getenv("AI_LLM_ENABLED", "true").lower() == "true"


def normalize_language(language) -> str:
    """Неизвестный язык не должен ронять генерацию — откатываемся на русский."""
    value = (language or DEFAULT_LANGUAGE).lower()
    return value if value in LANGUAGE_INSTRUCTIONS else DEFAULT_LANGUAGE


def resolve_provider() -> str:
    """
    Возвращает 'gemini' или 'openai'. Бросает LlmUnavailableError,
    если ни один ключ не задан.
    """
    configured = os.getenv("AI_LLM_PROVIDER", "auto").lower()
    gemini_key = os.getenv("GEMINI_API_KEY")
    openai_key = os.getenv("OPENAI_API_KEY")

    if configured == "gemini":
        if not gemini_key:
            raise LlmUnavailableError("Gemini API is not configured")
        return "gemini"
    if configured == "openai":
        if not openai_key:
            raise LlmUnavailableError("OpenAI API is not configured")
        return "openai"

    if gemini_key:
        return "gemini"
    if openai_key:
        return "openai"
    raise LlmUnavailableError("LLM API is not configured")


def build_prompt(application: dict, scoring: dict, language: str = DEFAULT_LANGUAGE) -> str:
    language = normalize_language(language)
    impacts = IMPACT_LABELS[language]
    top_factors = scoring.get("explanation", {}).get("top_factors", [])
    factors_text = "\n".join(
        f"  - {f['feature']}: {f['value']} "
        f"({impacts['positive'] if f['impact'] == 'positive' else impacts['negative']}, "
        f"{f['shap_value']:+.2f})"
        for f in top_factors
    )

    risk = RISK_LABELS[language].get(scoring.get("risk_level", "medium"), "medium")

    return f"""Ты — аналитик кредитного отдела АО «Аграрная кредитная корпорация» программы «Кең дала 2».

Данные заявки:
- Заявитель: {application.get('applicant_name', 'не указан')}
- ИИН/БИН: {application.get('iin_bin', '—')}
- Регион: {application.get('region')}
- Вид продукции: {application.get('product_type')}
- Площадь земли: {application.get('land_area')} га
- Запрашиваемая сумма: {int(application.get('requested_amount', 0)):,} ₸

Результат ИИ-скоринга:
- Балл: {scoring.get('score')} / 100
- Уровень риска: {risk}
- Рекомендация: {'одобрить' if scoring.get('approved') else 'отказать'}
- Рекомендуемая сумма: {int(scoring.get('recommended_amount') or 0):,} ₸

Топ-3 фактора влияния:
{factors_text}

{LANGUAGE_INSTRUCTIONS[language]}
"""


def _generate_with_openai(prompt: str) -> str:
    if not OPENAI_AVAILABLE or OpenAI is None:
        raise LlmConfigurationError("OpenAI Python package is not installed")

    request_id = correlation_id.get()
    client = OpenAI(
        api_key=os.getenv("OPENAI_API_KEY"),
        timeout=OPENAI_TIMEOUT_SECONDS,
        max_retries=OPENAI_MAX_RETRIES,
        default_headers={"X-Correlation-ID": request_id} if request_id else None,
    )
    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=300,
        temperature=0.3,
    )
    content = response.choices[0].message.content
    if not content or not content.strip():
        raise LlmUnavailableError("OpenAI API returned an empty conclusion")
    return content.strip()


def _generate_with_gemini(prompt: str) -> str:
    """
    Вызов Gemini через стандартный urllib: отдельный SDK ради одного POST
    тянуть не хочется, а ключ передаётся query-параметром.
    """
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise LlmUnavailableError("Gemini API is not configured")

    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.3,
            # Gemini 3 тратит часть бюджета на "мысли" (thoughtsTokenCount),
            # поэтому лимит с запасом, иначе заключение обрывается на полуслове.
            "maxOutputTokens": GEMINI_MAX_OUTPUT_TOKENS,
            "thinkingConfig": {"thinkingLevel": "low"},
        },
    }).encode("utf-8")

    url = GEMINI_ENDPOINT.format(model=GEMINI_MODEL) + "?key=" + api_key
    headers = {"Content-Type": "application/json"}
    request_id = correlation_id.get()
    if request_id:
        headers["X-Correlation-ID"] = request_id

    request = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=GEMINI_TIMEOUT_SECONDS) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        # Тело ошибки Google содержит ключ в URL, поэтому наружу отдаём только код.
        raise LlmUnavailableError(f"Gemini API returned HTTP {exc.code}") from None
    except (urllib.error.URLError, TimeoutError) as exc:
        raise LlmUnavailableError("Gemini API is unreachable") from exc
    except json.JSONDecodeError as exc:
        raise LlmUnavailableError("Gemini API returned malformed JSON") from exc

    candidates = body.get("candidates") or []
    if not candidates:
        raise LlmUnavailableError("Gemini API returned no candidates")
    parts = candidates[0].get("content", {}).get("parts") or []
    text = "".join(part.get("text", "") for part in parts).strip()
    if not text:
        raise LlmUnavailableError("Gemini API returned an empty conclusion")
    return text


def generate_conclusion(
    application: dict,
    scoring: dict,
    language: str = DEFAULT_LANGUAGE,
) -> str:
    """
    Генерирует текстовое заключение ИИ для комиссии.

    application: данные заявки (region, product_type, land_area, requested_amount, ...)
    scoring: результат /score endpoint
    language: ru | kz | en — язык итогового текста

    Возвращает строку с заключением.
    """
    if not llm_enabled():
        raise LlmUnavailableError("LLM conclusion is disabled")

    provider = resolve_provider()
    prompt = build_prompt(application, scoring, language)

    if provider == "gemini":
        return _generate_with_gemini(prompt)
    return _generate_with_openai(prompt)
