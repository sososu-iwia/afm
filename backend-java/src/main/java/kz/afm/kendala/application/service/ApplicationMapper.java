package kz.afm.kendala.application.service;

import kz.afm.kendala.application.dto.ApplicationResponse;
import kz.afm.kendala.application.dto.DocumentResponse;
import kz.afm.kendala.application.dto.StatusHistoryResponse;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.ApplicationStatusHistory;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMapper {

    public ApplicationResponse toResponse(Application application) {
        return toResponse(application, List.of());
    }

    public ApplicationResponse toResponse(Application application, List<DocumentResponse> documents) {
        return new ApplicationResponse(
                application.getId(),
                application.getApplicationNumber(),
                application.getApplicant().getId(),
                application.getStatus(),
                application.getIinOrBin(),
                application.getRegion(),
                application.getProductionType(),
                application.getActivityType(),
                application.getApplicantCategory(),
                application.getLandArea(),
                application.getRequestedAmount(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                documents,
                application.getSubmissionRevision()
        );
    }

    public StatusHistoryResponse toResponse(ApplicationStatusHistory history) {
        return new StatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getChangedBy().getId(),
                history.getReason(),
                history.getComment(),
                history.getCreatedAt()
        );
    }
}
