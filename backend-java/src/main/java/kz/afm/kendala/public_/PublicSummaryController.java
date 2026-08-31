package kz.afm.kendala.public_;

import java.math.BigDecimal;
import java.util.List;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.repository.ApplicationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicSummaryController {

    private final ApplicationRepository applicationRepository;

    public PublicSummaryController(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/analytics")
    public AnalyticsSummary getAnalytics() {
        List<Application> all = applicationRepository.findAll();
        long total = all.size();
        long approved = all.stream().filter(a -> a.getStatus() == ApplicationStatus.APPROVED).count();
        long rejected = all.stream().filter(a -> a.getStatus() == ApplicationStatus.REJECTED).count();
        long inReview = all.stream().filter(a -> a.getStatus() == ApplicationStatus.IN_REVIEW).count();
        long draft = all.stream().filter(a -> a.getStatus() == ApplicationStatus.DRAFT).count();
        BigDecimal totalAmount = all.stream()
                .map(Application::getRequestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AnalyticsSummary(total, approved, rejected, inReview, draft, totalAmount);
    }

    public record AnalyticsSummary(
            long total,
            long approved,
            long rejected,
            long inReview,
            long draft,
            BigDecimal totalAmount
    ) {}
}
