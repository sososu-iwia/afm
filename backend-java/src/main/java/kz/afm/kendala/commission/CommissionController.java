package kz.afm.kendala.commission;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.commission.dto.CommissionApplicationDetail;
import kz.afm.kendala.commission.dto.CommissionApplicationListItem;
import kz.afm.kendala.commission.dto.DecisionRequest;
import kz.afm.kendala.common.CurrentUserProvider;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.ai.ProtocolService;
import kz.afm.kendala.ai.dto.ProtocolResult;
import kz.afm.kendala.idempotency.IdempotencyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commission")
@Tag(name = "Commission")
@SecurityRequirement(name = "bearerAuth")
public class CommissionController {

    private final CommissionService commissionService;
    private final CurrentUserProvider currentUserProvider;
    private final ProtocolService protocolService;
    private final CommissionExportService exportService;
    private final IdempotencyService idempotencyService;

    public CommissionController(
            CommissionService commissionService,
            CurrentUserProvider currentUserProvider,
            ProtocolService protocolService,
            CommissionExportService exportService,
            IdempotencyService idempotencyService
    ) {
        this.commissionService = commissionService;
        this.currentUserProvider = currentUserProvider;
        this.protocolService = protocolService;
        this.exportService = exportService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/applications")
    @Operation(summary = "Получить очередь комиссии")
    @PreAuthorize("hasAnyRole('COMMISSION_MEMBER','CHAIRMAN','SECRETARY','ADMIN')")
    public PageResponse<CommissionApplicationListItem> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        User user = currentUserProvider.getCurrentUser();
        return commissionService.list(page - 1, size, status, region, minAmount, maxAmount, sortBy, sortDir, user);
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "Получить полную карточку заявки")
    @PreAuthorize("hasAnyRole('COMMISSION_MEMBER','CHAIRMAN','SECRETARY','ADMIN')")
    public CommissionApplicationDetail getDetail(@PathVariable UUID id) {
        User user = currentUserProvider.getCurrentUser();
        return commissionService.getDetail(id, user);
    }

    @GetMapping("/applications/export")
    @Operation(summary = "Экспортировать реестр комиссии в XLSX")
    @PreAuthorize("hasAnyRole('CHAIRMAN','SECRETARY','ADMIN')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "ru") String language
    ) {
        User user = currentUserProvider.getCurrentUser();
        byte[] content = exportService.export(status, region, minAmount, maxAmount, user, language);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"kendala-applications-" + language + ".xlsx\"")
                .contentLength(content.length)
                .body(content);
    }

    @PostMapping("/applications/{id}/approve")
    @Operation(summary = "Одобрить заявку", description = "Только CHAIRMAN/ADMIN и только из IN_REVIEW.")
    @ApiResponse(responseCode = "409", description = "Заявка не готова к финальному решению")
    @PreAuthorize("hasAnyRole('CHAIRMAN','ADMIN')")
    public CommissionApplicationDetail approve(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionRequest request,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserProvider.getCurrentUser();
        return idempotencyService.execute(
                idempotencyKey,
                user.getId(),
                "POST /api/commission/applications/{id}/approve",
                new DecisionPayload(id, request),
                CommissionApplicationDetail.class,
                () -> commissionService.approve(
                        id,
                        user,
                        request != null ? request.reason() : null,
                        request != null ? request.approvedAmount() : null
                )
        );
    }

    @PostMapping("/applications/{id}/reject")
    @Operation(summary = "Отклонить заявку", description = "Только CHAIRMAN/ADMIN и только из IN_REVIEW.")
    @ApiResponse(responseCode = "409", description = "Заявка не готова к финальному решению")
    @PreAuthorize("hasAnyRole('CHAIRMAN','ADMIN')")
    public CommissionApplicationDetail reject(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionRequest request,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserProvider.getCurrentUser();
        return idempotencyService.execute(
                idempotencyKey,
                user.getId(),
                "POST /api/commission/applications/{id}/reject",
                new DecisionPayload(id, request),
                CommissionApplicationDetail.class,
                () -> commissionService.reject(id, user, request != null ? request.reason() : null)
        );
    }

    @PostMapping("/applications/{id}/request-documents")
    @Operation(summary = "Запросить дополнительные документы",
            description = "Доступно из SUBMITTED или IN_REVIEW.")
    @PreAuthorize("hasAnyRole('CHAIRMAN','SECRETARY','ADMIN')")
    public CommissionApplicationDetail requestDocuments(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionRequest request,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserProvider.getCurrentUser();
        return idempotencyService.execute(
                idempotencyKey,
                user.getId(),
                "POST /api/commission/applications/{id}/request-documents",
                new DecisionPayload(id, request),
                CommissionApplicationDetail.class,
                () -> commissionService.requestDocuments(
                        id,
                        user,
                        request != null ? request.reason() : null,
                        request != null ? request.documentTypes() : java.util.List.of()
                )
        );
    }

    @PostMapping("/applications/{id}/generate-protocol")
    @Operation(summary = "Сформировать или скачать кэшированный PDF-протокол")
    @PreAuthorize("hasAnyRole('CHAIRMAN','SECRETARY','ADMIN')")
    public ResponseEntity<byte[]> generateProtocol(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "ru") String language,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserProvider.getCurrentUser();
        ProtocolResult result = idempotencyService.execute(
                idempotencyKey,
                user.getId(),
                "POST /api/commission/applications/{id}/generate-protocol",
                new ProtocolPayload(id, language),
                ProtocolResult.class,
                () -> protocolService.generate(id, user, language)
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, result.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.fileName().replace("\"", "_") + "\"")
                .contentLength(result.content().length)
                .body(result.content());
    }

    @PatchMapping("/applications/{id}/publish")
    @Operation(summary = "Опубликовать одобренную заявку")
    @PreAuthorize("hasAnyRole('CHAIRMAN','ADMIN')")
    public CommissionApplicationDetail publish(@PathVariable UUID id) {
        return commissionService.publish(id, currentUserProvider.getCurrentUser());
    }

    @PatchMapping("/applications/{id}/unpublish")
    @Operation(summary = "Снять заявку с публикации")
    @PreAuthorize("hasAnyRole('CHAIRMAN','ADMIN')")
    public CommissionApplicationDetail unpublish(@PathVariable UUID id) {
        return commissionService.unpublish(id, currentUserProvider.getCurrentUser());
    }

    private record DecisionPayload(UUID applicationId, DecisionRequest request) {
    }

    private record ProtocolPayload(UUID applicationId, String language) {
    }
}
