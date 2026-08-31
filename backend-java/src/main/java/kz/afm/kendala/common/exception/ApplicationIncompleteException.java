package kz.afm.kendala.common.exception;

import java.util.List;

public class ApplicationIncompleteException extends RuntimeException {

    private final List<String> missingFields;
    private final List<String> missingDocuments;

    public ApplicationIncompleteException(List<String> missingFields, List<String> missingDocuments) {
        super("APPLICATION_INCOMPLETE");
        this.missingFields = missingFields;
        this.missingDocuments = missingDocuments;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public List<String> getMissingDocuments() {
        return missingDocuments;
    }
}
