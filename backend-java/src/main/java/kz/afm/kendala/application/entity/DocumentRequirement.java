package kz.afm.kendala.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import kz.afm.kendala.application.enums.DocumentType;

@Entity
@Table(name = "document_requirements")
public class DocumentRequirement {

    @Id
    private UUID id;

    @Column(name = "applicant_type", nullable = false, length = 64)
    private String applicantType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 64)
    private DocumentType documentType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "condition_expression", length = 512)
    private String conditionExpression;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "requirement_version", nullable = false)
    private int requirementVersion;

    public UUID getId() { return id; }
    public String getApplicantType() { return applicantType; }
    public DocumentType getDocumentType() { return documentType; }
    public boolean isRequired() { return required; }
    public String getConditionExpression() { return conditionExpression; }
    public boolean isActive() { return active; }
    public int getRequirementVersion() { return requirementVersion; }
}
