package kz.afm.kendala.publicregistry;

import java.util.Map;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.common.PaginationPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicRegistryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "decisionDate", "COALESCE(a.decision_at, a.updated_at)",
            "approvedAmount", "COALESCE(a.approved_amount, a.requested_amount)",
            "applicationNumber", "a.application_number",
            "region", "a.region",
            "productionType", "a.production_type",
            "activityType", "a.activity_type"
    );

    private final PublicRegistryRepository repository;

    public PublicRegistryService(PublicRegistryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicApprovedApplicationResponse> findApproved(
            PublicRegistryFilter filter,
            int requestedPage,
            int requestedSize,
            String sortBy,
            String sortDir
    ) {
        int page = PaginationPolicy.oneBased(requestedPage);
        int size = Math.min(Math.max(requestedSize, 1), MAX_PAGE_SIZE);
        int offset = (page - 1) * size;
        String sortColumn = SORT_COLUMNS.getOrDefault(
                sortBy,
                "COALESCE(a.decision_at, a.updated_at)"
        );
        boolean ascending = "asc".equalsIgnoreCase(sortDir);
        PublicRegistryRepository.PageSlice slice = repository.findApproved(
                filter, offset, size, sortColumn, ascending);
        int totalPages = slice.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) slice.totalElements() / size);
        return new PageResponse<>(
                slice.content(),
                page,
                size,
                slice.totalElements(),
                totalPages
        );
    }
}
