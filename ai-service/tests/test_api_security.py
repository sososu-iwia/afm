from fastapi.testclient import TestClient

from api import main, ocr


client = TestClient(main.app)


def test_health_is_available():
    response = client.get("/health", headers={"X-Correlation-ID": "test-request-123"})
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.headers["X-Correlation-ID"] == "test-request-123"


def test_public_ai_endpoints_are_disabled_by_default(monkeypatch):
    monkeypatch.setattr(main, "PUBLIC_ENDPOINTS_ENABLED", False)
    response = client.post(
        "/score",
        json={
            "region": "Akmola",
            "product_type": "Wheat",
            "land_area": 10,
            "requested_amount": 1000,
        },
    )
    assert response.status_code == 404


def test_internal_endpoint_requires_api_key(monkeypatch):
    monkeypatch.setattr(main, "INTERNAL_API_KEY", "expected-key")
    response = client.post("/internal/score", json={})
    assert response.status_code == 401


def test_internal_endpoint_rejects_invalid_api_key(monkeypatch):
    monkeypatch.setattr(main, "INTERNAL_API_KEY", "expected-key")
    response = client.post(
        "/internal/duplicates",
        headers={"X-Internal-Api-Key": "wrong-key"},
        json={"new_application": {}, "existing_applications": []},
    )
    assert response.status_code == 401


def test_internal_ocr_rejects_oversized_upload(monkeypatch):
    monkeypatch.setattr(main, "INTERNAL_API_KEY", "test-key")
    monkeypatch.setattr(main, "MAX_UPLOAD_BYTES", 4)
    response = client.post(
        "/internal/ocr?documentId=00000000-0000-0000-0000-000000000001",
        headers={"X-Internal-Api-Key": "test-key"},
        files={"file": ("document.pdf", b"%PDF-oversized", "application/pdf")},
    )
    assert response.status_code == 413


def test_missing_scoring_model_fails_closed(monkeypatch):
    monkeypatch.setattr(main, "INTERNAL_API_KEY", "test-key")

    def missing_model():
        raise FileNotFoundError("private/model/path")

    monkeypatch.setattr(main, "load_scoring_dependencies", missing_model)
    response = client.post(
        "/internal/score",
        headers={"X-Internal-Api-Key": "test-key"},
        json={
            "applicationId": "00000000-0000-0000-0000-000000000001",
            "iinOrBin": "000000000000",
            "region": "Akmola",
            "productionType": "GRAIN",
            "landArea": 10,
            "requestedAmount": 1000,
        },
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "Модель не обучена."
    assert "private/model/path" not in response.text


def test_internal_score_does_not_require_openai_and_preserves_missing_optional_data(monkeypatch):
    monkeypatch.setattr(main, "INTERNAL_API_KEY", "test-key")
    captured = {}

    def fake_loader():
        def load_models_cached():
            return object(), object(), {}

        def score_application(app_dict, *_):
            captured["app"] = app_dict
            return {
                "score": 78.0,
                "risk_level": "low",
                "approved": True,
                "approval_probability": 0.91,
                "recommended_amount": 700000,
                "data_availability": {key: value is not None for key, value in app_dict.items()},
            }

        def shap(_app_dict, top_n=3):
            return {"top_factors": [{"feature": "Полнота документов", "impact": "positive", "value": "100%", "shap_value": 0.2}]}

        def metadata():
            return {
                "modelName": "test-model",
                "version": "demo-unverified",
                "trainingDate": None,
                "featureList": [],
                "datasetVersionHash": None,
                "evaluationMetrics": {},
                "artifactHash": "abc",
                "verificationStatus": "DEMO",
            }

        def indicators(app_dict):
            return [{"field": "credit_history_years", "value": app_dict["credit_history_years"], "source": "JAVA_BACKEND", "receivedAt": "now", "verificationStatus": "MISSING"}]

        return load_models_cached, score_application, shap, metadata, indicators

    monkeypatch.setattr(main, "load_scoring_dependencies", fake_loader)
    response = client.post(
        "/internal/score",
        headers={"X-Internal-Api-Key": "test-key"},
        json={
            "applicationId": "00000000-0000-0000-0000-000000000001",
            "iinOrBin": "000000000000",
            "region": "Akmola",
            "productionType": "GRAIN",
            "landArea": 10,
            "requestedAmount": 1000,
            "creditHistoryYears": None,
            "previousLoansCount": None,
            "previousLoansRepaid": None,
            "taxDebt": None,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["score"] == 78.0
    assert body["llmSummary"] is None
    assert body["modelName"] == "test-model"
    assert captured["app"]["credit_history_years"] is None
    assert captured["app"]["prev_loans_count"] is None
    assert captured["app"]["tax_debt"] is None


def test_internal_duplicate_detection(monkeypatch):
    monkeypatch.setattr(main, "INTERNAL_API_KEY", "test-key")
    response = client.post(
        "/internal/duplicates",
        headers={"X-Internal-Api-Key": "test-key"},
        json={
            "new_application": {
                "application_id": "new",
                "iin_bin": "123456789012",
                "region": "Akmola",
                "product_type": "Пшеница",
                "land_area": 100,
                "requested_amount": 1000,
                "status": "submitted",
            },
            "existing_applications": [{
                "application_id": "old",
                "iin_bin": "123456789012",
                "region": "Akmola",
                "product_type": "Пшеница",
                "land_area": 100,
                "requested_amount": 1000,
                "status": "IN_REVIEW",
            }],
        },
    )

    assert response.status_code == 200
    assert response.json()["hasDuplicates"] is True
    assert response.json()["matchedApplicationId"] == "old"


def test_ocr_error_does_not_expose_exception_details(monkeypatch):
    monkeypatch.setattr(ocr, "OCR_AVAILABLE", True)
    monkeypatch.setattr(ocr, "check_tesseract", lambda: True)

    def fail_to_open(_):
        raise RuntimeError("private OCR path")

    monkeypatch.setattr(ocr.Image, "open", fail_to_open)
    result = ocr.extract_text_from_image(b"invalid image")

    assert result["error"] == "Image processing failed"
    assert "private OCR path" not in str(result)
