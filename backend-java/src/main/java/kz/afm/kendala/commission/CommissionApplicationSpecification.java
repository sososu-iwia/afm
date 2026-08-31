package kz.afm.kendala.commission;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.enums.ApplicationStatus;
import org.springframework.data.jpa.domain.Specification;

public final class CommissionApplicationSpecification {

    public static final Set<ApplicationStatus> VISIBLE_STATUSES = Set.of(
            ApplicationStatus.SUBMITTED,
            ApplicationStatus.IN_REVIEW,
            ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED,
            ApplicationStatus.APPROVED,
            ApplicationStatus.REJECTED
    );

    private CommissionApplicationSpecification() {}

    public static Specification<Application> forCommission(
            ApplicationStatus statusFilter,
            String regionFilter,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(root.get("status").in(VISIBLE_STATUSES));

            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (regionFilter != null && !regionFilter.isBlank()) {
                predicates.add(cb.equal(root.get("region"), regionFilter));
            }
            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("requestedAmount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("requestedAmount"), maxAmount));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
