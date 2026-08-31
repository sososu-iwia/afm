package kz.afm.kendala.application.service;

import java.io.IOException;
import java.util.Set;
import kz.afm.kendala.common.exception.FileValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    private static final byte[] PDF_MAGIC  = {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final long maxBytes;

    public FileValidator(@Value("${app.document.max-size-bytes:10485760}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileValidationException("FILE_EMPTY", "Файл не может быть пустым");
        }
        if (file.getSize() > maxBytes) {
            throw new FileValidationException("FILE_TOO_LARGE",
                    "Размер файла превышает допустимый предел " + (maxBytes / 1024) + " КБ");
        }

        String ext = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new FileValidationException("UNSUPPORTED_FORMAT",
                    "Неподдерживаемый формат файла. Разрешены: PDF, JPG, PNG");
        }

        String declared = file.getContentType();
        String expected = expectedContentType(ext);
        if (declared == null || !declared.split(";")[0].trim().equalsIgnoreCase(expected)) {
            throw new FileValidationException("INVALID_CONTENT_TYPE",
                    "Тип содержимого не соответствует расширению файла");
        }

        try {
            byte[] bytes = file.getBytes();
            if (!matchesMagic(bytes, ext)) {
                throw new FileValidationException("INVALID_FILE_CONTENT",
                        "Содержимое файла не соответствует заявленному формату");
            }
        } catch (FileValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new FileValidationException("FILE_READ_ERROR", "Ошибка чтения файла");
        }
    }

    public String sanitizeFileName(String rawName) {
        if (rawName == null) return "document";
        if (rawName.contains("/") || rawName.contains("\\") || rawName.contains("..")) {
            throw new FileValidationException("UNSAFE_FILE_NAME", "Недопустимое имя файла");
        }
        String clean = rawName.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
        if (clean.length() > 255) clean = clean.substring(0, 255);
        return clean.isEmpty() ? "document" : clean;
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private static String expectedContentType(String ext) {
        return switch (ext) {
            case "pdf"        -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png"        -> "image/png";
            default           -> "";
        };
    }

    private static boolean matchesMagic(byte[] bytes, String ext) {
        if (bytes.length < 8) return false;
        return switch (ext) {
            case "pdf"         -> startsWith(bytes, PDF_MAGIC);
            case "jpg", "jpeg" -> startsWith(bytes, JPEG_MAGIC);
            case "png"         -> startsWith(bytes, PNG_MAGIC);
            default            -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }
}
