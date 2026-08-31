package kz.afm.kendala.ai;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tags;
import io.swagger.v3.oas.annotations.tags.Tag;
import kz.afm.kendala.ai.dto.ApplicationProcessingResponse;
import kz.afm.kendala.ai.dto.OcrStatusResponse;
import kz.afm.kendala.ai.dto.ScoreStatusResponse;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.common.CurrentUserProvider;
import kz.afm.kendala.idempotency.IdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('APPLICANT','COMMISSION_MEMBER','CHAIRMAN','SECRETARY','ADMIN')")
@Tags({@Tag(name = "Application Processing"), @Tag(name = "AI Jobs")})
@SecurityRequirement(name = "bearerAuth")
public class AiController {
    private final AiOrchestrationService orchestrationService;
    private final AiJobStateService jobStateService;
    private final CurrentUserProvider currentUserProvider;
    private final IdempotencyService idempotencyService;

    public AiController(
            AiOrchestrationService orchestrationService,
            AiJobStateService jobStateService,
            CurrentUserProvider currentUserProvider,
            IdempotencyService idempotencyService
    ) {
        this.orchestrationService = orchestrationService;
        this.jobStateService = jobStateService;
        this.currentUserProvider = currentUserProvider;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/applications/{applicationId}/score")
    @Operation(summary = "Запустить scoring текущей ревизии",
            description = "Недоступно для APPROVED, REJECTED и WITHDRAWN; обязательный OCR должен быть успешен.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScoreStatusResponse triggerScore(
            @PathVariable UUID applicationId,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserProvider.getCurrentUser();
        return idempotencyService.execute(
                idempotencyKey,
                user.getId(),
                "POST /api/applications/{id}/score",
                new AiActionPayload(applicationId),
                ScoreStatusResponse.class,
                () -> orchestrationService.triggerScore(applicationId, user)
        );
    }

    @GetMapping("/applications/{applicationId}/score")
    @Operation(summary = "Получить scoring текущей ревизии")
    public ScoreStatusResponse getScore(@PathVariable UUID applicationId) {
        User user = currentUserProvider.getCurrentUser();
        return orchestrationService.getScore(applicationId, user);
    }

    @PostMapping("/applications/{applicationId}/llm-conclusion/retry")
    @Operation(summary = "Повторно запросить текстовое заключение",
            description = "Скоринг детерминирован и не пересчитывается; повторяется только заключение LLM.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('COMMISSION_MEMBER','CHAIRMAN','SECRETARY','ADMIN')")
    public ScoreStatusResponse retryLlmConclusion(@PathVariable UUID applicationId) {
        User user = currentUserProvider.getCurrentUser();
        return orchestrationService.retryLlmConclusion(applicationId, user);
    }

    @GetMapping("/applications/{applicationId}/processing")
    @Operation(summary = "Получить processing текущей submission revision")
    public ApplicationProcessingResponse getProcessing(@PathVariable UUID applicationId) {
        User user = currentUserProvider.getCurrentUser();
        return orchestrationService.getProcessing(applicationId, user);
    }

    @PostMapping("/documents/{documentId}/ocr")
    @Operation(summary = "Запустить OCR документа",
            description = "OCR immutable-документа может переиспользоваться между ревизиями; финальные заявки read-only.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OcrStatusResponse triggerOcr(
            @PathVariable UUID documentId,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserProvider.getCurrentUser();
        return idempotencyService.execute(
                idempotencyKey,
                user.getId(),
                "POST /api/documents/{id}/ocr",
                new AiActionPayload(documentId),
                OcrStatusResponse.class,
                () -> orchestrationService.triggerOcr(documentId, user)
        );
    }

    @GetMapping("/documents/{documentId}/ocr")
    @Operation(summary = "Получить результат OCR документа")
    public OcrStatusResponse getOcr(@PathVariable UUID documentId) {
        User user = currentUserProvider.getCurrentUser();
        return orchestrationService.getOcr(documentId, user);
    }

    @PostMapping("/ai/jobs/{jobId}/retry")
    @Operation(summary = "Повторить failed AI job",
            description = "Только ADMIN, только текущая нефинальная submission revision.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    public void retryJob(@PathVariable UUID jobId) {
        jobStateService.manualRetry(jobId, currentUserProvider.getCurrentUser());
    }

    private record AiActionPayload(UUID entityId) {
    }
}
