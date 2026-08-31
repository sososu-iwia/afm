"""
FastAPI AI-сервис для скоринга заявок.

Запуск:
    uvicorn api.main:app --reload --port 8001

Внутренние эндпоинты:
    POST /internal/score    — скоринг по контракту с Java-бэком
    POST /internal/ocr      — OCR по контракту с Java-бэком

Опциональные публичные эндпоинты:
    POST /score             — скоринг заявки + SHAP + заключение
    POST /check-document    — OCR-проверка документа
    POST /check-duplicates  — проверка на дубли и аномалии
    GET  /health            — проверка работоспособности
"""

import os
import re
import secrets
import sys
import uuid
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from fastapi import Depends, FastAPI, File, Header, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Optional
from pydantic import BaseModel, Field

from api.ocr import OcrUnavailableError, check_document
from api.duplicate_detector import ApplicationRecord, check_duplicates
from api.llm_conclusion import LlmUnavailableError, generate_conclusion
from api.request_context import correlation_id as correlation_id_context

IS_PRODUCTION = os.getenv("AI_ENV", "").lower() == "production"
PUBLIC_ENDPOINTS_ENABLED = os.getenv("AI_PUBLIC_ENDPOINTS_ENABLED", "false").lower() == "true"
MAX_UPLOAD_BYTES = int(os.getenv("AI_MAX_UPLOAD_BYTES", "10485760"))
SAFE_CORRELATION_ID = re.compile(r"^[A-Za-z0-9._:-]{1,100}$")

app = FastAPI(
    title="AFM AI Service",
    description="ИИ-скоринг заявок программы Кең дала 2",
    version="1.0.0",
    docs_url=None if IS_PRODUCTION else "/docs",
    redoc_url=None if IS_PRODUCTION else "/redoc",
    openapi_url=None if IS_PRODUCTION else "/openapi.json",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        origin.strip()
        for origin in os.getenv("AI_ALLOWED_ORIGINS", "http://localhost:8080").split(",")
        if origin.strip() and origin.strip() != "*"
    ],
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization", "X-Correlation-ID", "X-Internal-Api-Key"],
)

INTERNAL_API_KEY = os.getenv("AI_INTERNAL_API_KEY", "")


def load_scoring_dependencies():
    # Native ML libraries are loaded only for scoring requests, so health checks,
    # authentication failures and OCR remain available if the model runtime is broken.
    from models.scoring_model import (
        input_indicators,
        load_models_cached,
        model_metadata,
        score_application,
    )
    from models.shap_explainer import get_shap_explanation

    return load_models_cached, score_application, get_shap_explanation, model_metadata, input_indicators


@app.middleware("http")
async def correlation_id(request: Request, call_next):
    supplied = request.headers.get("X-Correlation-ID", "")
    value = supplied if SAFE_CORRELATION_ID.fullmatch(supplied) else str(uuid.uuid4())
    token = correlation_id_context.set(value)
    try:
        response = await call_next(request)
        response.headers["X-Correlation-ID"] = value
        return response
    finally:
        correlation_id_context.reset(token)


def require_internal_api_key(
    supplied_key: str = Header(default="", alias="X-Internal-Api-Key"),
):
    if not INTERNAL_API_KEY:
        raise HTTPException(status_code=503, detail="Internal AI API authentication is not configured")
    if not secrets.compare_digest(supplied_key, INTERNAL_API_KEY):
        raise HTTPException(status_code=401, detail="Invalid internal API key")


def require_public_endpoints():
    if not PUBLIC_ENDPOINTS_ENABLED:
        raise HTTPException(status_code=404, detail="Not found")


async def read_limited_upload(file: UploadFile) -> bytes:
    content = bytearray()
    while chunk := await file.read(65536):
        content.extend(chunk)
        if len(content) > MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail="Uploaded file is too large")
    return bytes(content)


class ApplicationInput(BaseModel):
    region: str = Field(..., examples=["Акмолинская область"])
    product_type: str = Field(..., examples=["Пшеница"])
    land_area: float = Field(..., gt=0, examples=[420])
    requested_amount: float = Field(..., gt=0, examples=[18000000])
    credit_history_years: Optional[float] = Field(default=None, ge=0, examples=[7])
    prev_loans_count: Optional[float] = Field(default=None, ge=0, examples=[3])
    prev_loans_repaid: Optional[float] = Field(default=None, ge=0, examples=[3])
    tax_debt: Optional[float] = Field(default=None, ge=0, le=1, examples=[0])
    doc_completeness: float = Field(default=1.0, ge=0, le=1, examples=[0.9])


class ScoringResult(BaseModel):
    score: float
    risk_level: str
    approved: bool
    approval_probability: float
    recommended_amount: Optional[int]
    explanation: dict


@app.get("/health")
def health():
    return {"status": "ok", "service": "AFM AI Scoring"}


@app.post(
    "/score",
    response_model=ScoringResult,
    dependencies=[Depends(require_public_endpoints)],
)
def score(application: ApplicationInput):
    """
    Скоринг заявки на кредит.

    Возвращает:
    - score: балл 0–100
    - risk_level: low / medium / high
    - approved: рекомендация (True/False)
    - approval_probability: вероятность одобрения
    - recommended_amount: рекомендуемая сумма кредита
    - explanation: топ-3 фактора SHAP + базовый балл
    """
    try:
        load_models_cached, score_application, get_shap_explanation, _, _ = (
            load_scoring_dependencies()
        )
        clf, reg, encoders = load_models_cached()
    except FileNotFoundError:
        raise HTTPException(status_code=503, detail="Scoring model unavailable")
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Scoring runtime unavailable") from exc

    app_dict = application.model_dump()

    try:
        result = score_application(app_dict, clf, reg, encoders)
        explanation = get_shap_explanation(app_dict, top_n=3)
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Scoring failed") from exc

    conclusion = None
    try:
        conclusion = generate_conclusion(app_dict, {**result, "explanation": explanation})
    except LlmUnavailableError:
        conclusion = None

    return ScoringResult(
        score=result["score"],
        risk_level=result["risk_level"],
        approved=result["approved"],
        approval_probability=result["approval_probability"],
        recommended_amount=result["recommended_amount"],
        explanation={**explanation, "conclusion": conclusion},
    )


class InternalScoreRequest(BaseModel):
    """Контракт запроса Java backend → Python AI service."""
    applicationId: str
    iinOrBin: str
    region: str
    productionType: str
    landArea: float
    requestedAmount: float
    creditHistoryYears: Optional[float] = None
    previousLoansCount: Optional[float] = None
    previousLoansRepaid: Optional[float] = None
    prevLoansCount: Optional[float] = None
    prevLoansRepaid: Optional[float] = None
    taxDebt: Optional[bool] = None
    docCompleteness: float = 1.0

    def loans_count(self) -> Optional[float]:
        return self.previousLoansCount if self.previousLoansCount is not None else self.prevLoansCount

    def loans_repaid(self) -> Optional[float]:
        return self.previousLoansRepaid if self.previousLoansRepaid is not None else self.prevLoansRepaid


PRODUCTION_TYPE_MAP = {
    "GRAIN": "Пшеница",
    "BARLEY": "Ячмень",
    "OILSEED": "Масличные культуры",
    "VEGETABLES": "Овощи",
    "DAIRY": "Молочная продукция",
    "Пшеница": "Пшеница",
    "Ячмень": "Ячмень",
    "Масличные культуры": "Масличные культуры",
    "Овощи": "Овощи",
    "Молочная продукция": "Молочная продукция",
}


@app.post("/internal/score", dependencies=[Depends(require_internal_api_key)])
def internal_score(req: InternalScoreRequest):
    """
    Внутренний эндпоинт для Java backend.
    Принимает его формат → считает скоринг → возвращает его формат.
    """
    try:
        load_models_cached, score_application, get_shap_explanation, model_metadata, input_indicators = (
            load_scoring_dependencies()
        )
        clf, reg, encoders = load_models_cached()
    except FileNotFoundError:
        raise HTTPException(status_code=503, detail="Модель не обучена.")
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Scoring runtime unavailable") from exc

    product_type = PRODUCTION_TYPE_MAP.get(req.productionType)
    if product_type is None:
        raise HTTPException(status_code=422, detail="Unsupported productionType")

    app_dict = {
        "region": req.region,
        "product_type": product_type,
        "land_area": req.landArea,
        "requested_amount": req.requestedAmount,
        "credit_history_years": req.creditHistoryYears,
        "prev_loans_count": req.loans_count(),
        "prev_loans_repaid": req.loans_repaid(),
        "tax_debt": None if req.taxDebt is None else (1.0 if req.taxDebt else 0.0),
        "doc_completeness": req.docCompleteness,
    }

    try:
        result = score_application(app_dict, clf, reg, encoders)
        explanation = get_shap_explanation(app_dict, top_n=3)
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Scoring failed") from exc

    risk_category_map = {"low": "LOW", "medium": "MEDIUM", "high": "HIGH"}
    top_factors = [
        {
            "factor": f["feature"],
            "weight": abs(f["shap_value"]),
            "direction": f["impact"],
            "value": f.get("value"),
        }
        for f in explanation["top_factors"]
    ]
    metadata = model_metadata()

    return {
        "applicationId": req.applicationId,
        "score": result["score"],
        "riskCategory": risk_category_map.get(result["risk_level"], "MEDIUM"),
        "recommendedAmount": result["recommended_amount"],
        "topFactors": top_factors,
        "llmSummary": None,
        "modelName": metadata["modelName"],
        "modelVersion": metadata["version"],
        "modelMetadata": {
            **metadata,
            "dataAvailability": result["data_availability"],
            "inputIndicators": input_indicators(app_dict),
        },
    }


@app.post("/internal/ocr", dependencies=[Depends(require_internal_api_key)])
async def internal_ocr(file: UploadFile = File(...), documentId: str = ""):
    """
    Внутренний эндпоинт для Java-бэка: OCR документа.
    Java присылает файл → Python возвращает результат OCR.
    """
    content = await read_limited_upload(file)
    try:
        result = check_document(content, file.filename or "document")
    except OcrUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return {
        "documentId": documentId,
        "readable": result["ok"],
        "issues": result["issues"],
        "extractedText": result.get("ocr", {}).get("text", ""),
        "confidence": result.get("ocr", {}).get("confidence", 0),
        "cropped": result.get("ocr", {}).get("cropped", False),
    }


@app.post("/check-document", dependencies=[Depends(require_public_endpoints)])
async def check_document_endpoint(file: UploadFile = File(...)):
    """OCR-проверка загружаемого документа."""
    content = await read_limited_upload(file)
    try:
        result = check_document(content, file.filename or "document")
    except OcrUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return result


class DuplicateCheckRequest(BaseModel):
    new_application: dict
    existing_applications: List[dict] = Field(default_factory=list)


@app.post("/internal/duplicates", dependencies=[Depends(require_internal_api_key)])
def internal_duplicates(request: DuplicateCheckRequest):
    """Внутренняя authenticated проверка дублей для Java job queue."""
    new_app = ApplicationRecord(**request.new_application)
    existing = [ApplicationRecord(**a) for a in request.existing_applications]
    result = check_duplicates(new_app, existing)
    return {
        "applicationId": new_app.application_id,
        "hasDuplicates": result.is_duplicate,
        "duplicateType": result.duplicate_type,
        "matchedApplicationId": result.matched_application_id,
        "flags": result.flags,
        "anomalies": result.anomalies,
    }


class InternalLlmConclusionRequest(BaseModel):
    applicationId: str
    region: str
    productionType: str
    landArea: float
    requestedAmount: float
    score: float
    riskCategory: str
    recommendedAmount: Optional[float] = None
    topFactors: list[dict] = Field(default_factory=list)
    language: Optional[str] = None


@app.post("/internal/llm-conclusion", dependencies=[Depends(require_internal_api_key)])
def internal_llm_conclusion(request: InternalLlmConclusionRequest):
    """Optional LLM conclusion. Failure here must not affect deterministic scoring."""
    risk_map = {"LOW": "low", "MEDIUM": "medium", "HIGH": "high"}
    application = {
        "region": request.region,
        "product_type": PRODUCTION_TYPE_MAP.get(request.productionType, request.productionType),
        "land_area": request.landArea,
        "requested_amount": request.requestedAmount,
    }
    explanation = {
        "top_factors": [
            {
                "feature": factor.get("factor") or factor.get("feature") or "factor",
                "value": "" if factor.get("value") is None else factor.get("value"),
                "impact": factor.get("direction") or factor.get("impact") or "positive",
                "shap_value": float(factor.get("weight") or factor.get("shap_value") or 0),
            }
            for factor in request.topFactors
        ]
    }
    scoring = {
        "score": request.score,
        "risk_level": risk_map.get(request.riskCategory, "medium"),
        "approved": request.riskCategory == "LOW",
        "recommended_amount": request.recommendedAmount,
        "explanation": explanation,
    }
    try:
        text = generate_conclusion(application, scoring, language=request.language)
    except LlmUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return {"applicationId": request.applicationId, "text": text}


@app.post("/check-duplicates", dependencies=[Depends(require_public_endpoints)])
def check_duplicates_endpoint(request: DuplicateCheckRequest):
    """Проверка заявки на дубли и аномалии."""
    new_app = ApplicationRecord(**request.new_application)
    existing = [ApplicationRecord(**a) for a in request.existing_applications]
    result = check_duplicates(new_app, existing)
    return {
        "is_duplicate": result.is_duplicate,
        "duplicate_type": result.duplicate_type,
        "matched_application_id": result.matched_application_id,
        "flags": result.flags,
        "anomalies": result.anomalies,
    }
