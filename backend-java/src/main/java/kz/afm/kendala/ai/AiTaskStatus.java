package kz.afm.kendala.ai;

public enum AiTaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    FAILED_OPTIONAL,
    SKIPPED
}
