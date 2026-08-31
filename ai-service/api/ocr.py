"""
OCR-модуль: извлечение текста из документов (PDF, JPG, PNG).
Использует Tesseract с поддержкой казахского и русского языков.

Проверяет:
- Читаемость документа
- Обрезан ли скан
- Минимальное количество текста
"""

import io
from pathlib import Path

try:
    from PIL import Image
    import pytesseract
    OCR_AVAILABLE = True
except ImportError:
    OCR_AVAILABLE = False

try:
    import fitz  # PyMuPDF для PDF
    PDF_AVAILABLE = True
except ImportError:
    PDF_AVAILABLE = False


MIN_TEXT_LENGTH = 50  # минимум символов для "читаемого" документа
MIN_CONFIDENCE = 40   # минимальный средний confidence Tesseract

class OcrUnavailableError(RuntimeError):
    pass


def check_tesseract() -> bool:
    """Проверяет наличие Tesseract в системе."""
    try:
        pytesseract.get_tesseract_version()
        return True
    except Exception:
        return False


def extract_text_from_image(image_bytes: bytes, lang: str = "rus+kaz") -> dict:
    """
    Извлекает текст из изображения.

    Возвращает:
        text: извлечённый текст
        confidence: средняя уверенность (0–100)
        readable: документ читаем
        cropped: документ обрезан (текст у края)
    """
    if not OCR_AVAILABLE:
        raise OcrUnavailableError("OCR Python dependencies are not installed")

    if not check_tesseract():
        raise OcrUnavailableError("Tesseract is not installed or not executable")

    try:
        image = Image.open(io.BytesIO(image_bytes))
        # Конвертируем в RGB если нужно
        if image.mode not in ("RGB", "L"):
            image = image.convert("RGB")

        # Получаем данные с confidence
        data = pytesseract.image_to_data(image, lang=lang, output_type=pytesseract.Output.DICT)
        text = pytesseract.image_to_string(image, lang=lang)

        # Считаем средний confidence по словам с conf > 0
        confidences = [int(c) for c in data["conf"] if int(c) > 0]
        avg_confidence = sum(confidences) / len(confidences) if confidences else 0

        # Проверяем обрезанность: есть ли текст у самого края (< 5% от размера)
        width, height = image.size
        edge_threshold_x = width * 0.05
        edge_threshold_y = height * 0.05
        is_cropped = False
        for i, word in enumerate(data["text"]):
            if word.strip():
                x, y = data["left"][i], data["top"][i]
                if x < edge_threshold_x or y < edge_threshold_y:
                    is_cropped = True
                    break

        readable = len(text.strip()) >= MIN_TEXT_LENGTH and avg_confidence >= MIN_CONFIDENCE

        return {
            "text": text.strip(),
            "confidence": round(avg_confidence, 1),
            "readable": readable,
            "cropped": is_cropped,
            "char_count": len(text.strip()),
        }

    except Exception:
        return {
            "text": "",
            "confidence": 0,
            "readable": False,
            "cropped": False,
            "char_count": 0,
            "error": "Image processing failed",
        }


def check_document(file_bytes: bytes, filename: str) -> dict:
    """
    Полная проверка документа: OCR + валидация.

    Возвращает:
        ok: документ прошёл проверку
        issues: список проблем
        ocr: результат OCR
    """
    issues = []
    ext = Path(filename).suffix.lower()

    if ext == ".pdf":
        if not PDF_AVAILABLE:
            raise OcrUnavailableError("PyMuPDF is required for PDF OCR")
        else:
            try:
                pdf = fitz.open(stream=file_bytes, filetype="pdf")
                page = pdf[0]
                pix = page.get_pixmap(dpi=150)
                img_bytes = pix.tobytes("png")
                ocr_result = extract_text_from_image(img_bytes)
            except OcrUnavailableError:
                raise
            except Exception:
                ocr_result = {
                    "text": "",
                    "readable": False,
                    "cropped": False,
                    "error": "PDF processing failed",
                }
    elif ext in (".jpg", ".jpeg", ".png"):
        ocr_result = extract_text_from_image(file_bytes)
    else:
        return {
            "ok": False,
            "issues": [f"Неподдерживаемый формат: {ext}"],
            "ocr": None,
        }

    if not ocr_result.get("readable"):
        issues.append("Документ нечитаем или содержит мало текста — возможно плохое качество скана")

    if ocr_result.get("cropped"):
        issues.append("Документ обрезан — часть содержимого может быть не видна")

    return {
        "ok": len(issues) == 0,
        "issues": issues,
        "ocr": ocr_result,
    }
