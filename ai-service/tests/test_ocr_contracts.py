import pytest

from api import ocr


def test_image_ocr_path_uses_image_extractor(monkeypatch):
    monkeypatch.setattr(
        ocr,
        "extract_text_from_image",
        lambda _content: {
            "text": "Оқылатын құжат " * 5,
            "confidence": 95,
            "readable": True,
            "cropped": False,
            "char_count": 80,
        },
    )

    result = ocr.check_document(b"\x89PNG\r\n\x1a\nimage", "scan.png")

    assert result["ok"] is True
    assert result["issues"] == []
    assert result["ocr"]["confidence"] == 95


def test_pdf_ocr_path_requires_pymupdf_and_uses_rendered_page(monkeypatch):
    if not ocr.PDF_AVAILABLE:
        pytest.skip("PyMuPDF is not installed")

    pdf = ocr.fitz.open()
    page = pdf.new_page()
    page.insert_text((72, 72), "Кең дала 2 PDF")
    pdf_bytes = pdf.tobytes()
    pdf.close()

    monkeypatch.setattr(
        ocr,
        "extract_text_from_image",
        lambda _content: {
            "text": "Кең дала PDF құжаты " * 5,
            "confidence": 91,
            "readable": True,
            "cropped": False,
            "char_count": 100,
        },
    )

    result = ocr.check_document(pdf_bytes, "document.pdf")

    assert result["ok"] is True
    assert result["ocr"]["confidence"] == 91
