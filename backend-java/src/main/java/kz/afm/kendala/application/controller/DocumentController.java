package kz.afm.kendala.application.controller;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kz.afm.kendala.application.dto.DocumentResponse;
import kz.afm.kendala.application.dto.DownloadResult;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.service.DocumentService;
import kz.afm.kendala.common.CurrentUserProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Documents")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUserProvider currentUserProvider;

    public DocumentController(DocumentService documentService, CurrentUserProvider currentUserProvider) {
        this.documentService = documentService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/applications/{applicationId}/documents")
    @Operation(summary = "Загрузить PDF, JPG или PNG документ")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('APPLICANT')")
    public DocumentResponse upload(
            @PathVariable UUID applicationId,
            @Parameter(description = "PDF, JPG или PNG", required = true,
                    schema = @Schema(type = "string", format = "binary"))
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType
    ) {
        User user = currentUserProvider.getCurrentUser();
        return documentService.upload(applicationId, file, documentType, user);
    }

    @GetMapping("/api/applications/{applicationId}/documents")
    @Operation(summary = "Получить документы заявки")
    public List<DocumentResponse> list(@PathVariable UUID applicationId) {
        User user = currentUserProvider.getCurrentUser();
        return documentService.list(applicationId, user);
    }

    @GetMapping("/api/documents/{documentId}")
    @Operation(summary = "Получить метаданные документа")
    public DocumentResponse getMetadata(@PathVariable UUID documentId) {
        User user = currentUserProvider.getCurrentUser();
        return documentService.getMetadata(documentId, user);
    }

    @GetMapping("/api/documents/{documentId}/download")
    @Operation(summary = "Скачать документ")
    public ResponseEntity<byte[]> download(@PathVariable UUID documentId) {
        User user = currentUserProvider.getCurrentUser();
        DownloadResult result = documentService.download(documentId, user);

        String safeName = result.originalFileName().replaceAll("[^\\w.\\- ]", "_").replace("\"", "_");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.contentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"");
        headers.setContentLength(result.size());

        return ResponseEntity.ok().headers(headers).body(result.content());
    }

    @DeleteMapping("/api/documents/{documentId}")
    @Operation(summary = "Удалить документ черновика")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('APPLICANT')")
    public void delete(@PathVariable UUID documentId) {
        User user = currentUserProvider.getCurrentUser();
        documentService.delete(documentId, user);
    }
}
