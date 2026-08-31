package kz.afm.kendala.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.enums.DocumentType;

public record CompletenessResult(
        boolean complete,
        @JsonProperty("isComplete")
        boolean isComplete,
        List<String> missingFields,
        List<String> missingDocuments,
        List<DocumentType> requiredTypes,
        List<DocumentType> uploadedTypes,
        List<DocumentType> missingTypes,
        List<UUID> invalidDocuments,
        List<UUID> processingDocuments,
        String message,
        Instant checkedAt
) {
    public CompletenessResult {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        missingDocuments = missingDocuments == null ? List.of() : List.copyOf(missingDocuments);
        requiredTypes = requiredTypes == null ? List.of() : List.copyOf(requiredTypes);
        uploadedTypes = uploadedTypes == null ? List.of() : List.copyOf(uploadedTypes);
        missingTypes = missingTypes == null ? List.of() : List.copyOf(missingTypes);
        invalidDocuments = invalidDocuments == null ? List.of() : List.copyOf(invalidDocuments);
        processingDocuments = processingDocuments == null ? List.of() : List.copyOf(processingDocuments);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }

    public CompletenessResult(
            boolean complete,
            List<String> missingFields,
            List<String> missingDocuments
    ) {
        this(
                complete,
                complete,
                missingFields,
                missingDocuments,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                complete ? "Заявка комплектна" : "Заявка требует заполнения обязательных полей или документов",
                Instant.now()
        );
    }
}
