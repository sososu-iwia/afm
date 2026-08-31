package kz.afm.kendala.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.entity.Application;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "application_scores")
public class ApplicationScore {
    @Id
    @Column(name = "application_id")
    private UUID applicationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", insertable = false, updatable = false)
    private Application application;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private AiProcessingJob job;

    @Column(name = "submission_revision", nullable = false)
    private int submissionRevision;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "risk_category", nullable = false, length = 32)
    private String riskCategory;

    @Column(name = "recommended_amount", precision = 19, scale = 2)
    private BigDecimal recommendedAmount;

    @Column(name = "top_factors", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String topFactors;

    @Column(name = "llm_summary")
    private String llmSummary;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "model_version", length = 128)
    private String modelVersion;

    @Column(name = "model_metadata", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String modelMetadata;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public AiProcessingJob getJob() { return job; }
    public void setJob(AiProcessingJob job) { this.job = job; }
    public int getSubmissionRevision() { return submissionRevision; }
    public void setSubmissionRevision(int submissionRevision) { this.submissionRevision = submissionRevision; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getRiskCategory() { return riskCategory; }
    public void setRiskCategory(String riskCategory) { this.riskCategory = riskCategory; }
    public BigDecimal getRecommendedAmount() { return recommendedAmount; }
    public void setRecommendedAmount(BigDecimal recommendedAmount) { this.recommendedAmount = recommendedAmount; }
    public String getTopFactors() { return topFactors; }
    public void setTopFactors(String topFactors) { this.topFactors = topFactors; }
    public String getLlmSummary() { return llmSummary; }
    public void setLlmSummary(String llmSummary) { this.llmSummary = llmSummary; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getModelMetadata() { return modelMetadata; }
    public void setModelMetadata(String modelMetadata) { this.modelMetadata = modelMetadata; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
}
