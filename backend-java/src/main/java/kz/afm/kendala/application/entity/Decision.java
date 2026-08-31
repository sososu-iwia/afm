package kz.afm.kendala.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.enums.DecisionType;
import kz.afm.kendala.application.enums.UserRole;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "decisions")
public class Decision {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by", nullable = false)
    private User decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, length = 64)
    private DecisionType decisionType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 64)
    private UserRole actorRole;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "requested_amount", precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "protocol_reference", length = 128)
    private String protocolReference;

    @Column(name = "submission_revision")
    private Integer submissionRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scoring_job_id")
    private kz.afm.kendala.ai.AiProcessingJob scoringJob;

    @Column(name = "ai_score", precision = 6, scale = 2)
    private BigDecimal aiScore;

    @Column(name = "ai_risk_category", length = 32)
    private String aiRiskCategory;

    @Column(name = "ai_recommended_amount", precision = 19, scale = 2)
    private BigDecimal aiRecommendedAmount;

    @Column(name = "ai_top_factors", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String aiTopFactors;

    @Column(name = "ai_llm_summary")
    private String aiLlmSummary;

    @Column(name = "ai_model_name", length = 128)
    private String aiModelName;

    @Column(name = "ai_model_version", length = 128)
    private String aiModelVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public User getDecidedBy() { return decidedBy; }
    public void setDecidedBy(User decidedBy) { this.decidedBy = decidedBy; }

    public DecisionType getDecisionType() { return decisionType; }
    public void setDecisionType(DecisionType decisionType) { this.decisionType = decisionType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UserRole getActorRole() { return actorRole; }
    public void setActorRole(UserRole actorRole) { this.actorRole = actorRole; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getProtocolReference() { return protocolReference; }
    public void setProtocolReference(String protocolReference) { this.protocolReference = protocolReference; }
    public Integer getSubmissionRevision() { return submissionRevision; }
    public void setSubmissionRevision(Integer submissionRevision) { this.submissionRevision = submissionRevision; }
    public kz.afm.kendala.ai.AiProcessingJob getScoringJob() { return scoringJob; }
    public void setScoringJob(kz.afm.kendala.ai.AiProcessingJob scoringJob) { this.scoringJob = scoringJob; }
    public BigDecimal getAiScore() { return aiScore; }
    public void setAiScore(BigDecimal aiScore) { this.aiScore = aiScore; }
    public String getAiRiskCategory() { return aiRiskCategory; }
    public void setAiRiskCategory(String aiRiskCategory) { this.aiRiskCategory = aiRiskCategory; }
    public BigDecimal getAiRecommendedAmount() { return aiRecommendedAmount; }
    public void setAiRecommendedAmount(BigDecimal aiRecommendedAmount) { this.aiRecommendedAmount = aiRecommendedAmount; }
    public String getAiTopFactors() { return aiTopFactors; }
    public void setAiTopFactors(String aiTopFactors) { this.aiTopFactors = aiTopFactors; }
    public String getAiLlmSummary() { return aiLlmSummary; }
    public void setAiLlmSummary(String aiLlmSummary) { this.aiLlmSummary = aiLlmSummary; }
    public String getAiModelName() { return aiModelName; }
    public void setAiModelName(String aiModelName) { this.aiModelName = aiModelName; }
    public String getAiModelVersion() { return aiModelVersion; }
    public void setAiModelVersion(String aiModelVersion) { this.aiModelVersion = aiModelVersion; }

    public Instant getCreatedAt() { return createdAt; }

    public Long getVersion() { return version; }
}
