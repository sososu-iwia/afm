package kz.afm.kendala.ai.dto;

public record OcrProcessingResponse(
        String status,
        int documentsTotal,
        int documentsProcessed,
        int documentsFailed
) {
}
