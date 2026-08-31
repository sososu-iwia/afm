package kz.afm.kendala.application.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.dto.DocumentResponse;
import kz.afm.kendala.application.dto.DownloadResult;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.common.exception.ApiException;
import kz.afm.kendala.common.exception.BusinessRuleException;
import kz.afm.kendala.common.exception.FileValidationException;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.filestore.StorageService;
import kz.afm.kendala.filestore.StoredFile;
import kz.afm.kendala.observability.DomainMetrics;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final FileValidator fileValidator;
    private final AuditService auditService;
    private final DomainMetrics metrics;
    private final ApplicationStatusPolicy statusPolicy;

    public DocumentService(
            ApplicationRepository applicationRepository,
            DocumentRepository documentRepository,
            StorageService storageService,
            FileValidator fileValidator,
            AuditService auditService,
            DomainMetrics metrics,
            ApplicationStatusPolicy statusPolicy
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.fileValidator = fileValidator;
        this.auditService = auditService;
        this.metrics = metrics;
        this.statusPolicy = statusPolicy;
    }

    @Transactional
    public DocumentResponse upload(UUID applicationId, MultipartFile file, DocumentType documentType, User currentUser) {
        Application app = getOwnedAppLocked(applicationId, currentUser);
        statusPolicy.requireDocumentUploadByApplicant(app, currentUser);
        if (documentType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Тип документа обязателен");
        }
        if (app.getStatus() == ApplicationStatus.DRAFT
                && documentRepository.existsByApplicationIdAndDocumentType(app.getId(), documentType)) {
            throw new BusinessRuleException(
                    "Документ типа " + documentType + " уже загружен для этой заявки",
                    "error.document-type-duplicate"
            );
        }

        fileValidator.validate(file);
        String sanitizedName = fileValidator.sanitizeFileName(file.getOriginalFilename());
        String rawCt = file.getContentType();
        String contentType = rawCt != null ? rawCt.split(";")[0].trim() : "application/octet-stream";

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FileValidationException("FILE_READ_ERROR", "Ошибка чтения файла");
        }
        String checksum = sha256(bytes);

        StoredFile stored = storageService.store(sanitizedName, contentType, bytes);
        registerRollbackCleanup(stored.storageKey());

        try {
            Document doc = new Document();
            doc.setApplication(app);
            doc.setDocumentType(documentType);
            doc.setOriginalFileName(sanitizedName);
            doc.setStorageKey(stored.storageKey());
            doc.setContentType(contentType);
            doc.setSize(stored.size());
            doc.setChecksum(checksum);
            doc.setUploadedBy(currentUser);
            documentRepository.saveAndFlush(doc);
            DocumentResponse response = DocumentResponse.from(doc);
            auditService.recordDomain(currentUser, "DOCUMENT_UPLOADED", "DOCUMENT",
                    doc.getId().toString(), null, response);
            metrics.increment("document.uploaded");
            return response;
        } catch (Exception e) {
            deleteUploadedFile(stored.storageKey());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID applicationId, User currentUser) {
        Application app = getAccessibleApp(applicationId, currentUser);
        return documentRepository.findByApplicationId(app.getId()).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getMetadata(UUID documentId, User currentUser) {
        return DocumentResponse.from(getAccessibleDoc(documentId, currentUser));
    }

    @Transactional(readOnly = true)
    public DownloadResult download(UUID documentId, User currentUser) {
        Document doc = getAccessibleDoc(documentId, currentUser);
        byte[] content = storageService.load(doc.getStorageKey());
        return new DownloadResult(content, doc.getContentType(), doc.getOriginalFileName(), doc.getSize());
    }

    @Transactional
    public void delete(UUID documentId, User currentUser) {
        Document doc = getOwnedDocLocked(documentId, currentUser);
        statusPolicy.requireDocumentDeleteByApplicant(doc.getApplication(), currentUser);
        DocumentResponse previousValue = DocumentResponse.from(doc);
        // Storage first: absent file treated as idempotent success; real error preserves DB record
        storageService.delete(doc.getStorageKey());
        documentRepository.delete(doc);
        auditService.recordDomain(currentUser, "DOCUMENT_DELETED", "DOCUMENT",
                doc.getId().toString(), previousValue, null);
    }

    private Application getOwnedAppLocked(UUID applicationId, User currentUser) {
        Application app = applicationRepository.findLockedById(applicationId)
                .orElseThrow(() -> applicationNotFound());
        statusPolicy.requireOwner(app, currentUser);
        return app;
    }

    private Application getAccessibleApp(UUID applicationId, User currentUser) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> applicationNotFound());
        if (statusPolicy.canCommissionRead(currentUser, app)) {
            return app;
        }
        if (currentUser != null && app.getApplicant().getId().equals(currentUser.getId())) {
            return app;
        }
        throw applicationNotFound();
    }

    private Document getOwnedDocLocked(UUID documentId, User currentUser) {
        Document doc = documentRepository.findLockedById(documentId)
                .orElseThrow(() -> documentNotFound());
        statusPolicy.requireOwner(doc.getApplication(), currentUser);
        return doc;
    }

    private Document getAccessibleDoc(UUID documentId, User currentUser) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> documentNotFound());
        if (statusPolicy.canCommissionRead(currentUser, doc.getApplication())) {
            return doc;
        }
        if (currentUser != null && doc.getApplication().getApplicant().getId().equals(currentUser.getId())) {
            return doc;
        }
        throw documentNotFound();
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        AtomicBoolean cleaned = new AtomicBoolean(false);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK && cleaned.compareAndSet(false, true)) {
                    deleteUploadedFile(storageKey);
                }
            }
        });
    }

    private void deleteUploadedFile(String storageKey) {
        try {
            storageService.delete(storageKey);
        } catch (Exception cleanupEx) {
            log.error("Failed to clean up orphaned storage key={} after DB rollback/failure", storageKey, cleanupEx);
        }
    }

    private NotFoundException applicationNotFound() {
        return new NotFoundException("Заявка не найдена", "error.application-not-found");
    }

    private NotFoundException documentNotFound() {
        return new NotFoundException("Документ не найден", "error.document-not-found");
    }
}
