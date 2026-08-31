package kz.afm.kendala.publicregistry;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import kz.afm.kendala.common.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public Registry")
public class PublicRegistryController {

    private final PublicRegistryService service;

    public PublicRegistryController(PublicRegistryService service) {
        this.service = service;
    }

    @GetMapping("/approved-applications")
    public PageResponse<PublicApprovedApplicationResponse> approvedApplications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String region,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String productionType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "decisionDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        PublicRegistryFilter filter = PublicRegistryFilter.of(
                region, productionType, dateFrom, dateTo, minAmount, maxAmount);
        return service.findApproved(filter, page, size, sortBy, sortDir);
    }
}
