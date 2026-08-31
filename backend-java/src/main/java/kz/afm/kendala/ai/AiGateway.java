package kz.afm.kendala.ai;

import java.util.UUID;
import kz.afm.kendala.ai.dto.AiDuplicateCheckRequest;
import kz.afm.kendala.ai.dto.AiDuplicateCheckResponse;
import kz.afm.kendala.ai.dto.AiLlmConclusionRequest;
import kz.afm.kendala.ai.dto.AiLlmConclusionResponse;
import kz.afm.kendala.ai.dto.AiOcrResponse;
import kz.afm.kendala.ai.dto.AiScoreRequest;
import kz.afm.kendala.ai.dto.AiScoreResponse;

public interface AiGateway {
    AiScoreResponse score(AiScoreRequest request);

    AiOcrResponse ocr(UUID documentId, String fileName, String contentType, byte[] content);

    AiDuplicateCheckResponse duplicateCheck(AiDuplicateCheckRequest request);

    AiLlmConclusionResponse llmConclusion(AiLlmConclusionRequest request);
}
