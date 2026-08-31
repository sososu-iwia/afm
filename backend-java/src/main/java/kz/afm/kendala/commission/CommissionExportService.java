package kz.afm.kendala.commission;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import kz.afm.kendala.ai.ProtocolMessages;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.service.ApplicationStatusPolicy;
import kz.afm.kendala.common.exception.ApiException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommissionExportService {
    private static final int EXPORT_LIMIT = 10_000;
    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusPolicy statusPolicy;

    public CommissionExportService(
            ApplicationRepository applicationRepository,
            ApplicationStatusPolicy statusPolicy
    ) {
        this.applicationRepository = applicationRepository;
        this.statusPolicy = statusPolicy;
    }

    @Transactional(readOnly = true)
    public byte[] export(
            ApplicationStatus status,
            String region,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            User viewer
    ) {
        return export(status, region, minAmount, maxAmount, viewer, ProtocolMessages.RU);
    }

    public byte[] export(
            ApplicationStatus status,
            String region,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            User viewer,
            String language
    ) {
        ProtocolMessages m = ProtocolMessages.of(language);
        statusPolicy.requireExportRole(viewer);
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER",
                    "minAmount не может быть больше maxAmount");
        }
        List<Application> applications = applicationRepository.findAll(
                CommissionApplicationSpecification.forCommission(status, region, minAmount, maxAmount),
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Applications");
            var headerStyle = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            String[] headers = {
                    m.get("application").replace(": ", ""),
                    m.get("applicant").replace(": ", ""),
                    m.get("iinBin").replace(": ", ""),
                    m.get("decision").replace(": ", ""),
                    m.get("region").replace(": ", ""),
                    m.get("product").replace(": ", ""),
                    m.get("area").replace(": ", ""),
                    m.get("requestedAmount").replace(": ", ""),
                    m.get("meetingDate").replace(": ", ""),
                    m.get("decisionDate").replace(": ", ""),
            };
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
                header.getCell(index).setCellStyle(headerStyle);
            }
            int rowNumber = 1;
            for (Application app : applications) {
                Row row = sheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(text(app.getApplicationNumber()));
                row.createCell(1).setCellValue(text(app.getApplicant().getFullName()));
                row.createCell(2).setCellValue(text(app.getIinOrBin()));
                row.createCell(3).setCellValue(m.decision(app.getStatus()));
                row.createCell(4).setCellValue(text(app.getRegion()));
                row.createCell(5).setCellValue(text(app.getProductionType()));
                if (app.getLandArea() != null) row.createCell(6).setCellValue(app.getLandArea().doubleValue());
                if (app.getRequestedAmount() != null) row.createCell(7).setCellValue(app.getRequestedAmount().doubleValue());
                row.createCell(8).setCellValue(app.getCreatedAt().toString());
                row.createCell(9).setCellValue(app.getUpdatedAt().toString());
            }
            for (int index = 0; index < headers.length; index++) sheet.autoSizeColumn(index);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Excel export failed", e);
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
