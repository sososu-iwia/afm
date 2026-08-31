package kz.afm.kendala.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kz.afm.kendala.ai.AiOperationType;
import kz.afm.kendala.ai.AiProcessingJobRepository;
import kz.afm.kendala.ai.AiTaskStatus;
import kz.afm.kendala.ai.DocumentOcrResultRepository;
import kz.afm.kendala.ai.ApplicationSnapshotRepository;
import kz.afm.kendala.application.dto.CompletenessResult;
import kz.afm.kendala.application.config.AppDocumentsProperties;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.entity.DocumentRequirement;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.repository.DocumentRequirementRepository;
import kz.afm.kendala.application.repository.AdditionalDocumentRequestRepository;
import kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ApplicationCompletenessService {

    private final DocumentRepository documentRepository;
    private final DocumentRequirementRepository requirementRepository;
    private final AppDocumentsProperties properties;
    private final DocumentOcrResultRepository ocrResultRepository;
    private final AiProcessingJobRepository aiJobRepository;
    private final AdditionalDocumentRequestRepository additionalDocumentRequestRepository;
    private final ApplicationSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public ApplicationCompletenessService(
            DocumentRepository documentRepository,
            DocumentRequirementRepository requirementRepository,
            AppDocumentsProperties properties,
            DocumentOcrResultRepository ocrResultRepository,
            AiProcessingJobRepository aiJobRepository,
            AdditionalDocumentRequestRepository additionalDocumentRequestRepository,
            ApplicationSnapshotRepository snapshotRepository,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.requirementRepository = requirementRepository;
        this.properties = properties;
        this.ocrResultRepository = ocrResultRepository;
        this.aiJobRepository = aiJobRepository;
        this.additionalDocumentRequestRepository = additionalDocumentRequestRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CompletenessResult check(Application application) {
        List<String> missingFields = new ArrayList<>();

        if (!StringUtils.hasText(application.getIinOrBin())) missingFields.add("iinOrBin");
        if (!StringUtils.hasText(application.getRegion())) missingFields.add("region");
        if (!StringUtils.hasText(application.getProductionType())) missingFields.add("productionType");
        if (application.getLandArea() == null) missingFields.add("landArea");
        if (application.getRequestedAmount() == null) missingFields.add("requestedAmount");

        List<Document> docs = documentRepository.findByApplicationId(application.getId());
        Set<DocumentType> uploadedTypeSet = docs.stream()
                .filter(d -> d.getStorageKey() != null && !d.getStorageKey().isBlank())
                .map(Document::getDocumentType)
                .collect(Collectors.toSet());
        List<DocumentType> uploadedTypes = uploadedTypeSet.stream()
                .sorted(Comparator.comparing(DocumentType::name))
                .toList();

        List<DocumentType> requiredTypes = resolveRequiredTypes(application);
        List<DocumentType> missingTypes = requiredTypes.stream()
                .filter(required -> !uploadedTypeSet.contains(required))
                .toList();
        List<String> missingDocuments = missingTypes.stream()
                .map(DocumentType::name)
                .toList();

        List<java.util.UUID> invalidDocuments = new ArrayList<>();
        List<java.util.UUID> processingDocuments = new ArrayList<>();
        List<Document> requiredDocuments = requiredTypes.stream()
                .map(type -> docs.stream()
                        .filter(document -> document.getDocumentType() == type)
                        .max(Comparator.comparing(Document::getCreatedAt))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        for (Document document : requiredDocuments) {
            inspectOcrState(application, document, invalidDocuments, processingDocuments);
        }

        boolean complete = missingFields.isEmpty()
                && missingDocuments.isEmpty()
                && invalidDocuments.isEmpty()
                && processingDocuments.isEmpty();

        return new CompletenessResult(
                complete,
                complete,
                missingFields,
                missingDocuments,
                requiredTypes,
                uploadedTypes,
                missingTypes,
                invalidDocuments,
                processingDocuments,
                message(complete, invalidDocuments, processingDocuments),
                Instant.now()
        );
    }

    private List<DocumentType> resolveRequiredTypes(Application application) {
        if (application.getSubmissionRevision() > 0
                && application.getStatus() != ApplicationStatus.DRAFT
                && application.getStatus() != ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED) {
            var snapshotTypes = snapshotRepository.findByApplicationIdAndSubmissionRevision(
                            application.getId(), application.getSubmissionRevision())
                    .flatMap(snapshot -> readSnapshotRequiredTypes(snapshot.getSnapshotJson()));
            if (snapshotTypes.isPresent()) {
                return snapshotTypes.get();
            }
        }

        return resolveCurrentRequiredTypes(application);
    }

    private List<DocumentType> resolveCurrentRequiredTypes(Application application) {
        LinkedHashSet<DocumentType> required = new LinkedHashSet<>();
        var configuredOverride = properties.getRequiredTypes();
        if (configuredOverride != null) {
            required.addAll(configuredOverride);
        } else {
            requirementRepository.findCurrentActive("GENERAL").stream()
                    .filter(DocumentRequirement::isRequired)
                    .filter(requirement -> matches(requirement.getConditionExpression(), application))
                    .map(DocumentRequirement::getDocumentType)
                    .forEach(required::add);

            String applicantType = application.getApplicantCategory() == null
                    ? "GENERAL"
                    : application.getApplicantCategory().name();
            if (!"GENERAL".equals(applicantType)) {
                requirementRepository.findCurrentActive(applicantType).stream()
                        .filter(DocumentRequirement::isRequired)
                        .filter(requirement -> matches(requirement.getConditionExpression(), application))
                        .map(DocumentRequirement::getDocumentType)
                        .forEach(required::add);
            }
        }

        additionalDocumentRequestRepository
                .findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(
                        application.getId(), AdditionalDocumentRequestStatus.OPEN)
                .ifPresent(request -> required.addAll(request.getRequestedDocumentTypes()));

        return required.stream()
                .sorted(Comparator.comparing(DocumentType::name))
                .toList();
    }

    public Map<String, Integer> currentRequirementVersions(Application application) {
        if (properties.getRequiredTypes() != null) {
            return Map.of();
        }
        Map<String, Integer> versions = new LinkedHashMap<>();
        addRequirementVersion(versions, "GENERAL", requirementRepository.findCurrentActive("GENERAL"));
        String applicantType = application.getApplicantCategory() == null
                ? "GENERAL"
                : application.getApplicantCategory().name();
        if (!"GENERAL".equals(applicantType)) {
            addRequirementVersion(
                    versions, applicantType, requirementRepository.findCurrentActive(applicantType));
        }
        return Map.copyOf(versions);
    }

    private void addRequirementVersion(
            Map<String, Integer> versions,
            String key,
            List<DocumentRequirement> requirements
    ) {
        requirements.stream()
                .mapToInt(DocumentRequirement::getRequirementVersion)
                .max()
                .ifPresent(version -> versions.put(key, version));
    }

    private Optional<List<DocumentType>> readSnapshotRequiredTypes(String snapshotJson) {
        try {
            var node = objectMapper.readTree(snapshotJson).path("completeness").path("requiredTypes");
            if (!node.isArray()) {
                return Optional.empty();
            }
            List<DocumentType> types = new ArrayList<>();
            node.forEach(value -> {
                try {
                    types.add(DocumentType.valueOf(value.asText()));
                } catch (IllegalArgumentException ignored) {
                    // Unknown historical values are ignored; known values remain reproducible.
                }
            });
            return Optional.of(types.stream().distinct()
                    .sorted(Comparator.comparing(DocumentType::name)).toList());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private void inspectOcrState(
            Application application,
            Document document,
            List<java.util.UUID> invalidDocuments,
            List<java.util.UUID> processingDocuments
    ) {
        var latestJob = aiJobRepository.findFirstByOperationTypeAndDocumentIdOrderByCreatedAtDesc(
                AiOperationType.OCR,
                document.getId()
        );
        if (latestJob.isEmpty()) {
            if (application.getStatus() == ApplicationStatus.SUBMITTED) {
                processingDocuments.add(document.getId());
            }
            return;
        }

        AiTaskStatus status = latestJob.get().getStatus();
        if (status == AiTaskStatus.PENDING
                || status == AiTaskStatus.PROCESSING
                || status == AiTaskStatus.FAILED_RETRYABLE) {
            processingDocuments.add(document.getId());
            return;
        }
        if (status == AiTaskStatus.FAILED
                || status == AiTaskStatus.FAILED_TERMINAL
                || status == AiTaskStatus.SKIPPED) {
            invalidDocuments.add(document.getId());
            return;
        }
        if (status == AiTaskStatus.COMPLETED) {
            boolean validResult = ocrResultRepository.findById(document.getId())
                    .filter(result -> result.getJob().getId().equals(latestJob.get().getId()))
                    .filter(kz.afm.kendala.ai.DocumentOcrResult::isReadable)
                    .isPresent();
            if (!validResult) {
                invalidDocuments.add(document.getId());
            }
        }
    }

    private boolean matches(String expression, Application application) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        String[] parts = expression.split("&&");
        for (String rawPart : parts) {
            if (!matchesSingle(rawPart.strip(), application)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesSingle(String expression, Application application) {
        String[] operators = {">=", "<=", "!=", "=", ">", "<"};
        for (String operator : operators) {
            int index = expression.indexOf(operator);
            if (index > 0) {
                String field = expression.substring(0, index).strip();
                String expected = unquote(expression.substring(index + operator.length()).strip());
                Object actual = value(field, application);
                if (actual == null) {
                    return false;
                }
                return compare(actual, operator, expected);
            }
        }
        return false;
    }

    private Object value(String field, Application application) {
        return switch (field) {
            case "iinOrBin" -> application.getIinOrBin();
            case "region" -> application.getRegion();
            case "productionType" -> application.getProductionType();
            case "activityType" -> application.getActivityType() == null ? null : application.getActivityType().name();
            case "applicantCategory", "applicantType" -> application.getApplicantCategory() == null
                    ? null
                    : application.getApplicantCategory().name();
            case "landArea" -> application.getLandArea();
            case "requestedAmount" -> application.getRequestedAmount();
            default -> null;
        };
    }

    private boolean compare(Object actual, String operator, String expected) {
        if (actual instanceof BigDecimal decimal) {
            BigDecimal expectedDecimal;
            try {
                expectedDecimal = new BigDecimal(expected);
            } catch (NumberFormatException e) {
                return false;
            }
            int comparison = decimal.compareTo(expectedDecimal);
            return numericComparison(operator, comparison);
        }

        int comparison = actual.toString().compareToIgnoreCase(expected);
        return switch (operator) {
            case "=" -> comparison == 0;
            case "!=" -> comparison != 0;
            default -> false;
        };
    }

    private boolean numericComparison(String operator, int comparison) {
        return switch (operator) {
            case "=" -> comparison == 0;
            case "!=" -> comparison != 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            default -> false;
        };
    }

    private String unquote(String value) {
        if ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String message(
            boolean complete,
            List<java.util.UUID> invalidDocuments,
            List<java.util.UUID> processingDocuments
    ) {
        if (complete) {
            return "Заявка комплектна";
        }
        if (!processingDocuments.isEmpty()) {
            return "Документы обрабатываются";
        }
        if (!invalidDocuments.isEmpty()) {
            return "Есть документы, которые не удалось распознать";
        }
        return "Заполните обязательные поля и загрузите обязательные документы";
    }
}
