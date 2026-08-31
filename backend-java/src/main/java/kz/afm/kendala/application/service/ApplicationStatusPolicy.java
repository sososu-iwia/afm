package kz.afm.kendala.application.service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.common.exception.ApiException;
import kz.afm.kendala.common.exception.BusinessRuleException;
import kz.afm.kendala.common.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStatusPolicy {

    private static final Set<UserRole> COMMISSION_READ_ROLES = EnumSet.of(
            UserRole.COMMISSION_MEMBER,
            UserRole.CHAIRMAN,
            UserRole.SECRETARY,
            UserRole.ADMIN
    );
    private static final Set<UserRole> FINAL_DECISION_ROLES = EnumSet.of(
            UserRole.CHAIRMAN,
            UserRole.ADMIN
    );
    private static final Set<UserRole> DOCUMENT_REQUEST_ROLES = EnumSet.of(
            UserRole.CHAIRMAN,
            UserRole.SECRETARY,
            UserRole.ADMIN
    );
    private static final Set<ApplicationStatus> COMMISSION_VISIBLE = EnumSet.of(
            ApplicationStatus.SUBMITTED,
            ApplicationStatus.IN_REVIEW,
            ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED,
            ApplicationStatus.APPROVED,
            ApplicationStatus.REJECTED
    );
    private static final Set<ApplicationStatus> DECIDABLE = EnumSet.of(
            ApplicationStatus.IN_REVIEW
    );
    private static final Set<ApplicationStatus> FINAL = EnumSet.of(
            ApplicationStatus.APPROVED,
            ApplicationStatus.REJECTED
    );

    public void requireApplicant(User user) {
        if (user == null || user.getRole() != UserRole.APPLICANT) {
            throw new AccessDeniedException("Действие доступно только заявителю");
        }
    }

    public void requireOwner(Application application, User user) {
        if (application == null
                || user == null
                || application.getApplicant() == null
                || !application.getApplicant().getId().equals(user.getId())) {
            throw applicationNotFound();
        }
    }

    public void requireEditableByApplicant(Application application, User user) {
        requireApplicant(user);
        requireOwner(application, user);
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw transitionForbidden("Редактировать можно только черновик");
        }
    }

    public void requireSubmittableByApplicant(Application application, User user) {
        requireApplicant(user);
        requireOwner(application, user);
        ApplicationStatus status = application.getStatus();
        if (status != ApplicationStatus.DRAFT
                && status != ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED) {
            throw transitionForbidden(
                    "Отправить можно только черновик или заявку с запрошенными дополнительными документами"
            );
        }
    }

    public void requireWithdrawableByApplicant(Application application, User user) {
        requireApplicant(user);
        requireOwner(application, user);
        if (application.getStatus() != ApplicationStatus.DRAFT
                && application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw transitionForbidden("Отозвать можно только черновик или отправленную заявку");
        }
    }

    public void requireDocumentUploadByApplicant(Application application, User user) {
        requireApplicant(user);
        requireOwner(application, user);
        ApplicationStatus status = application.getStatus();
        if (status != ApplicationStatus.DRAFT
                && status != ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED) {
            throw transitionForbidden(
                    "Загрузка документов доступна только для черновиков и заявок с запрошенными документами"
            );
        }
    }

    public void requireDocumentDeleteByApplicant(Application application, User user) {
        requireApplicant(user);
        requireOwner(application, user);
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw transitionForbidden("Удаление документов разрешено только до первой отправки заявки");
        }
    }

    public boolean canCommissionRead(User user, Application application) {
        return user != null
                && COMMISSION_READ_ROLES.contains(user.getRole())
                && application != null
                && COMMISSION_VISIBLE.contains(application.getStatus());
    }

    public void requireCommissionList(User user) {
        if (user == null || !COMMISSION_READ_ROLES.contains(user.getRole())) {
            throw new AccessDeniedException("Доступ к реестру комиссии запрещен");
        }
    }

    public void requireCommissionRead(User user, Application application) {
        requireCommissionList(user);
        if (!COMMISSION_VISIBLE.contains(application.getStatus())) {
            throw applicationNotFound();
        }
    }

    public void requireExportRole(User user) {
        if (user == null || !DOCUMENT_REQUEST_ROLES.contains(user.getRole())) {
            throw new AccessDeniedException("Экспорт доступен только секретарю, председателю или администратору");
        }
    }

    public void requireFinalDecisionRole(User user) {
        if (user == null || !FINAL_DECISION_ROLES.contains(user.getRole())) {
            throw new AccessDeniedException("Финальное решение доступно только председателю или администратору");
        }
    }

    public void requireDocumentRequestRole(User user) {
        if (user == null || !DOCUMENT_REQUEST_ROLES.contains(user.getRole())) {
            throw new AccessDeniedException("Запрос документов доступен только секретарю, председателю или администратору");
        }
    }

    public void requireDecidable(Application application) {
        if (FINAL.contains(application.getStatus())) {
            throw new BusinessRuleException(
                    "Статус " + application.getStatus() + " является финальным: изменение запрещено",
                    "error.commission-conflict"
            );
        }
        if (!DECIDABLE.contains(application.getStatus())) {
            throw new BusinessRuleException(
                    "Действие недоступно для заявки в статусе " + application.getStatus(),
                    "error.commission-conflict"
            );
        }
    }

    public void requireDocumentsRequestable(Application application) {
        if (FINAL.contains(application.getStatus())) {
            throw new BusinessRuleException(
                    "Статус " + application.getStatus() + " является финальным: изменение запрещено",
                    "error.commission-conflict"
            );
        }
        if (application.getStatus() != ApplicationStatus.SUBMITTED
                && application.getStatus() != ApplicationStatus.IN_REVIEW) {
            throw new BusinessRuleException(
                    "Запрос документов недоступен для заявки в статусе " + application.getStatus(),
                    "error.commission-conflict"
            );
        }
    }

    public void requireApprovedAmountAllowed(BigDecimal requestedAmount, BigDecimal approvedAmount) {
        if (approvedAmount == null || approvedAmount.signum() <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Одобренная сумма должна быть больше нуля"
            );
        }
        if (requestedAmount != null && approvedAmount.compareTo(requestedAmount) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Одобренная сумма не может превышать запрошенную сумму без отдельного основания"
            );
        }
    }

    public boolean isCommissionVisible(ApplicationStatus status) {
        return COMMISSION_VISIBLE.contains(status);
    }

    private BusinessRuleException transitionForbidden(String message) {
        return new BusinessRuleException(message, "error.status-transition-forbidden");
    }

    private NotFoundException applicationNotFound() {
        return new NotFoundException("Заявка не найдена", "error.application-not-found");
    }
}
