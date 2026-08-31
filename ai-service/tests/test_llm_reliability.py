import json
import urllib.error
from types import SimpleNamespace

from api import llm_conclusion
from api.request_context import correlation_id


def test_openai_client_uses_bounded_retry_timeout_and_correlation(monkeypatch):
    captured = {}

    class FakeCompletions:
        def create(self, **kwargs):
            captured["request"] = kwargs
            return SimpleNamespace(
                choices=[SimpleNamespace(message=SimpleNamespace(content="Conclusion"))]
            )

    class FakeOpenAI:
        def __init__(self, **kwargs):
            captured["client"] = kwargs
            self.chat = SimpleNamespace(completions=FakeCompletions())

    monkeypatch.setattr(llm_conclusion, "OPENAI_AVAILABLE", True)
    monkeypatch.setattr(llm_conclusion, "OpenAI", FakeOpenAI)
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("AI_LLM_PROVIDER", "openai")
    token = correlation_id.set("corr-test-001")
    try:
        result = llm_conclusion.generate_conclusion(
            {"region": "Akmola", "requested_amount": 1000},
            {"score": 80, "approved": True, "explanation": {"top_factors": []}},
        )
    finally:
        correlation_id.reset(token)

    assert result == "Conclusion"
    assert captured["client"]["timeout"] == llm_conclusion.OPENAI_TIMEOUT_SECONDS
    assert captured["client"]["max_retries"] == llm_conclusion.OPENAI_MAX_RETRIES
    assert captured["client"]["default_headers"]["X-Correlation-ID"] == "corr-test-001"


def test_missing_openai_package_returns_configuration_error(monkeypatch):
    monkeypatch.setenv("AI_LLM_ENABLED", "true")
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("AI_LLM_PROVIDER", "openai")
    monkeypatch.setattr(llm_conclusion, "OPENAI_AVAILABLE", False)
    monkeypatch.setattr(llm_conclusion, "OpenAI", None)

    try:
        llm_conclusion.generate_conclusion(
            {"region": "Akmola", "requested_amount": 1000},
            {"score": 80, "approved": True, "explanation": {"top_factors": []}},
        )
    except llm_conclusion.LlmConfigurationError as exc:
        assert "OpenAI Python package is not installed" in str(exc)
    else:
        raise AssertionError("Expected LlmConfigurationError")


def test_gemini_provider_is_used_when_key_present(monkeypatch):
    captured = {}

    class FakeResponse:
        def read(self):
            return json.dumps(
                {"candidates": [{"content": {"parts": [{"text": "Қорытынды"}]}}]}
            ).encode("utf-8")

        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

    def fake_urlopen(request, timeout=None):
        captured["url"] = request.full_url
        captured["body"] = json.loads(request.data.decode("utf-8"))
        return FakeResponse()

    monkeypatch.setenv("AI_LLM_PROVIDER", "auto")
    monkeypatch.setenv("GEMINI_API_KEY", "gem-key")
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setattr(llm_conclusion.urllib.request, "urlopen", fake_urlopen)

    result = llm_conclusion.generate_conclusion(
        {"region": "Akmola", "requested_amount": 1000},
        {"score": 80, "approved": True, "explanation": {"top_factors": []}},
        language="kz",
    )

    assert result == "Қорытынды"
    assert llm_conclusion.GEMINI_MODEL in captured["url"]
    # Казахская инструкция должна попасть в промпт целиком.
    prompt = captured["body"]["contents"][0]["parts"][0]["text"]
    assert "қазақ тілінде" in prompt


def test_gemini_http_error_does_not_leak_the_api_key(monkeypatch):
    def fake_urlopen(request, timeout=None):
        raise urllib.error.HTTPError(request.full_url, 400, "Bad Request", {}, None)

    monkeypatch.setenv("AI_LLM_PROVIDER", "gemini")
    monkeypatch.setenv("GEMINI_API_KEY", "super-secret-key")
    monkeypatch.setattr(llm_conclusion.urllib.request, "urlopen", fake_urlopen)

    try:
        llm_conclusion.generate_conclusion(
            {"region": "Akmola", "requested_amount": 1000},
            {"score": 80, "approved": True, "explanation": {"top_factors": []}},
        )
    except llm_conclusion.LlmUnavailableError as exc:
        assert "super-secret-key" not in str(exc)
        assert "HTTP 400" in str(exc)
    else:
        raise AssertionError("Expected LlmUnavailableError")


def test_unknown_language_falls_back_to_russian():
    prompt = llm_conclusion.build_prompt(
        {"region": "Akmola", "requested_amount": 1000},
        {"score": 80, "approved": True, "explanation": {"top_factors": []}},
        language="fr",
    )
    assert "на русском языке" in prompt


def test_no_provider_configured_raises(monkeypatch):
    monkeypatch.setenv("AI_LLM_PROVIDER", "auto")
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    try:
        llm_conclusion.resolve_provider()
    except llm_conclusion.LlmUnavailableError as exc:
        assert "not configured" in str(exc)
    else:
        raise AssertionError("Expected LlmUnavailableError")
