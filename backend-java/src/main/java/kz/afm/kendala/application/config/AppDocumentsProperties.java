package kz.afm.kendala.application.config;

import java.util.List;
import kz.afm.kendala.application.enums.DocumentType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.documents")
public class AppDocumentsProperties {

    private List<DocumentType> requiredTypes;

    public List<DocumentType> getRequiredTypes() {
        return requiredTypes;
    }

    public void setRequiredTypes(List<DocumentType> requiredTypes) {
        this.requiredTypes = requiredTypes;
    }
}
