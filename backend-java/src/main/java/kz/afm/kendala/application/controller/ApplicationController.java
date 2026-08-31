package kz.afm.kendala.application.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.dto.ApplicationCreateRequest;
import kz.afm.kendala.application.dto.AdditionalDocumentRequestResponse;
import kz.afm.kendala.application.dto.ApplicationResponse;
import kz.afm.kendala.application.dto.ApplicationUpdateRequest;
import kz.afm.kendala.application.dto.CompletenessResult;
import kz.afm.kendala.application.dto.StatusHistoryResponse;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.service.ApplicationService;
import kz.afm.kendala.application.service.AdditionalDocumentRequestService;
import kz.afm.kendala.common.CurrentUserProvider;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.idempotency.IdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
@PreAuthorize("hasRole('APPLICANT')")
@Tag(name = "Applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final CurrentUserProvider currentUserProvider;
    private final IdempotencyService idempotencyService;
    private final AdditionalDocumentRequestService documentRequestService;

    public ApplicationController(
            ApplicationService applicationService,
            CurrentUserProvider currentUserProvider,
            IdempotencyService idempotencyService,
            AdditionalDocumentRequestService documentRequestService
    ) {
        this.applicationService = applicationService;
        this.currentUserProvider = currentUserProvider;
        this.idempotencyService = idempotencyService;
        this.documentRequestService = documentRequestService;
    }

    @PostMapping
    @Operation(summary = "Создать черновик заявки")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody ApplicationCreateRequest request) {
        User currentUser = currentUserProvider.getCurrentUser();
        return applicationService.create(request, currentUser);
    }

    @GetMapping
    @Operation(summary = "Получить заявки текущего заявителя")
    public PageResponse<ApplicationResponse> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User currentUser = currentUserProvider.getCurrentUser();
        return applicationService.findAll(currentUser, page - 1, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить заявку")
    public ApplicationResponse getById(@PathVariable UUID id) {
        User currentUser = currentUserProvider.getCurrentUser();
        return applicationService.getById(id, currentUser);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Изменить черновик заявки")
    public ApplicationResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationUpdateRequest request
    ) {
        User currentUser = currentUserProvider.getCurrentUser();
        return applicationService.update(id, request, currentUser);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Подать или повторно подать заявку",
            description = "Успешная операция атомарно создаёт новую submission revision и запускает её AI workflow.")
    public ApplicationResponse submit(
            @PathVariable UUID id,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User currentUser = currentUserProvider.getCurrentUser();
        return idempotencyService.execute(
                idempotencyKey,
                currentUser.getId(),
                "POST /api/applications/{id}/submit",
                new ApplicationActionPayload(id),
                ApplicationResponse.class,
                () -> applicationService.submit(id, currentUser)
        );
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Отозвать заявку")
    public ApplicationResponse withdraw(
            @PathVariable UUID id,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User currentUser = currentUserProvider.getCurrentUser();
        return idempotencyService.execute(
                idempotencyKey,
                currentUser.getId(),
                "POST /api/applications/{id}/withdraw",
                new ApplicationActionPayload(id),
                ApplicationResponse.class,
                () -> applicationService.withdraw(id, currentUser)
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable UUID id) {
        User currentUser = currentUserProvider.getCurrentUser();
        applicationService.deleteDraft(id, currentUser);
    }

    @GetMapping("/{id}/status-history")
    @Operation(summary = "Получить историю статусов")
    public List<StatusHistoryResponse> getStatusHistory(@PathVariable UUID id) {
        User currentUser = currentUserProvider.getCurrentUser();
        return applicationService.getStatusHistory(id, currentUser);
    }

    @GetMapping("/{id}/completeness")
    @Operation(summary = "Проверить комплектность заявки")
    public CompletenessResult getCompleteness(@PathVariable UUID id) {
        User currentUser = currentUserProvider.getCurrentUser();
        return applicationService.getCompleteness(id, currentUser);
    }

    @GetMapping("/{id}/document-requests")
    @Operation(summary = "Получить историю запросов дополнительных документов")
    @PreAuthorize("hasAnyRole('APPLICANT','COMMISSION_MEMBER','SECRETARY','CHAIRMAN','ADMIN')")
    public List<AdditionalDocumentRequestResponse> getDocumentRequests(@PathVariable UUID id) {
        return documentRequestService.list(id, currentUserProvider.getCurrentUser());
    }

    private record ApplicationActionPayload(UUID applicationId) {
    }
}
