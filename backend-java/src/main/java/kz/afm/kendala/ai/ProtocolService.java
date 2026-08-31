package kz.afm.kendala.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kz.afm.kendala.ai.dto.ProtocolResult;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.Decision;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.DecisionRepository;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.commission.CommissionApplicationSpecification;
import kz.afm.kendala.common.exception.BusinessRuleException;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.common.exception.ServiceUnavailableException;
import kz.afm.kendala.filestore.StorageService;
import kz.afm.kendala.filestore.StoredFile;
import kz.afm.kendala.observability.DomainMetrics;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProtocolService {
    private static final String TEMPLATE_VERSION = "v1";
    private static final Set<ApplicationStatus> FINAL =
            Set.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Almaty"));

    private static final List<Path> SYSTEM_FONT_CANDIDATES = List.of(
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf"),
            Path.of("/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"),
            Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"),
            Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"),
            Path.of("/Library/Fonts/Arial Unicode.ttf")
    );

    private final ApplicationRepository applicationRepository;
    private final DecisionRepository decisionRepository;
    private final GeneratedProtocolRepository protocolRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final DomainMetrics metrics;
    private final ObjectMapper objectMapper;
    private final String unicodeFontPath;

    public ProtocolService(
            ApplicationRepository applicationRepository,
            DecisionRepository decisionRepository,
            GeneratedProtocolRepository protocolRepository,
            StorageService storageService,
            AuditService auditService,
            DomainMetrics metrics,
            ObjectMapper objectMapper,
            @Value("${app.pdf.unicode-font-path:}") String unicodeFontPath
    ) {
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.protocolRepository = protocolRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.unicodeFontPath = unicodeFontPath;
    }

    @Transactional
    public ProtocolResult generate(UUID applicationId, User generatedBy) {
        return generate(applicationId, generatedBy, ProtocolMessages.RU);
    }

    public ProtocolResult generate(UUID applicationId, User generatedBy, String language) {
        Application app = applicationRepository.findLockedById(applicationId)
                .filter(candidate -> CommissionApplicationSpecification.VISIBLE_STATUSES
                        .contains(candidate.getStatus()))
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        if (!FINAL.contains(app.getStatus())) {
            throw new BusinessRuleException("Протокол доступен только после финального решения");
        }
        ProtocolMessages messages = ProtocolMessages.of(language);
        String fileName = safeProtocolFileName(app.getApplicationNumber(), messages.language());
        // Протокол кэшируется навсегда, но версия на другом языке — отдельный документ.
        GeneratedProtocol existing = protocolRepository
                .findByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream()
                .filter(candidate -> messages.language().equals(candidate.getLanguage()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return new ProtocolResult(
                    storageService.load(existing.getStorageKey()),
                    existing.getFileName(),
                    existing.getContentType()
            );
        }

        byte[] pdf = buildPdf(app, generatedBy, messages);
        StoredFile stored = storageService.store(fileName, "application/pdf", pdf);
        try {
            GeneratedProtocol protocol = new GeneratedProtocol();
            protocol.setApplication(app);
            protocol.setGeneratedBy(generatedBy);
            protocol.setStorageKey(stored.storageKey());
            protocol.setFileName(fileName);
            protocol.setContentType("application/pdf");
            protocol.setSize(stored.size());
            protocol.setGeneratedAt(Instant.now());
            protocol.setChecksum(sha256(pdf));
            protocol.setTemplateVersion(TEMPLATE_VERSION);
            protocol.setLanguage(messages.language());
            protocol.setFinalizedAt(Instant.now());
            protocol.setFinalizedBy(generatedBy);
            protocol.setProtocolNumber("KD-" + app.getApplicationNumber());
            protocolRepository.saveAndFlush(protocol);
            auditService.recordDomain(generatedBy, "PROTOCOL_GENERATED", "APPLICATION",
                    app.getId().toString(), null, Map.of(
                            "protocolId", protocol.getId(),
                            "protocolNumber", protocol.getProtocolNumber(),
                            "templateVersion", protocol.getTemplateVersion(),
                            "checksum", protocol.getChecksum(),
                            "fileName", fileName,
                            "contentType", protocol.getContentType(),
                            "size", protocol.getSize()));
            auditService.recordDomain(generatedBy, "PROTOCOL_FINALIZED", "APPLICATION",
                    app.getId().toString(), null, Map.of(
                            "protocolId", protocol.getId(),
                            "protocolNumber", protocol.getProtocolNumber()));
            metrics.increment("protocol.generated");
        } catch (Exception exception) {
            storageService.delete(stored.storageKey());
            throw exception;
        }
        return new ProtocolResult(pdf, fileName, "application/pdf");
    }

    private byte[] buildPdf(Application app, User generatedBy, ProtocolMessages m) {
        List<Decision> decisions = decisionRepository.findByApplicationIdOrderByCreatedAtAsc(app.getId());
        Decision finalDecision = decisions.stream()
                .filter(decision -> decision.getDecisionType()
                        != kz.afm.kendala.application.enums.DecisionType.ADDITIONAL_DOCUMENTS_REQUESTED)
                .reduce((first, second) -> second)
                .orElse(null);
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = loadUnicodeFont(document);
            PdfWriter writer = new PdfWriter(document, font, m);

            writer.heading(m.get("title"));
            if (!ProtocolMessages.KZ.equals(m.language())) {
                // Казахский подзаголовок остаётся в русской и английской версиях как
                // двуязычный реквизит документа.
                writer.line(ProtocolMessages.of(ProtocolMessages.KZ).get("title"));
            }
            writer.line(m.get("programme"));
            writer.blank();
            writer.line(m.get("meetingNumber") + app.getApplicationNumber());
            writer.line(m.get("meetingDate") + DATE_FORMAT.format(
                    app.getDecisionAt() == null ? app.getUpdatedAt() : app.getDecisionAt()));
            writer.line(m.get("generatedBy") + safeText(generatedBy.getFullName())
                    + " (" + m.role(generatedBy.getRole()) + ")");
            writer.blank();

            writer.section(m.get("committee"));
            writer.line(m.get("chairman"));
            writer.line(m.get("secretary"));
            writer.line(m.get("members"));
            writer.blank();

            writer.section(m.get("applicationsSection"));
            writer.line(m.get("application") + app.getApplicationNumber());
            writer.line(m.get("revision") + (finalDecision == null
                    ? app.getSubmissionRevision() : finalDecision.getSubmissionRevision()));
            writer.line(m.get("applicant") + safeText(app.getApplicant().getFullName()));
            writer.line(m.get("iinBin") + maskIinBin(app.getIinOrBin()));
            writer.line(m.get("region") + safeText(app.getRegion()));
            writer.line(m.get("activity") + m.activity(app.getActivityType()));
            writer.line(m.get("category") + m.category(app.getApplicantCategory()));
            writer.line(m.get("product") + safeText(app.getProductionType()));
            writer.line(m.get("area") + amount(app.getLandArea()) + m.get("areaUnit"));
            writer.line(m.get("requestedAmount") + amount(app.getRequestedAmount()) + " KZT");
            writer.line(m.get("approvedAmount") + amount(app.getApprovedAmount()) + " KZT");
            writer.blank();

            writer.section(m.get("decisionSection"));
            if (decisions.isEmpty()) {
                writer.line(m.get("decision") + m.decision(app.getStatus()));
            } else {
                for (Decision decision : decisions) {
                    writer.line(m.get("decision") + m.decision(decision.getDecisionType()));
                    writer.line(m.get("decisionDate") + DATE_FORMAT.format(decision.getCreatedAt()));
                    writer.line(m.get("decidedBy") + safeText(decision.getDecidedBy().getFullName()));
                    writer.line(m.get("reason") + safeText(decision.getReason()));
                }
            }
            writer.blank();

            writer.section(m.get("aiSection"));
            if (finalDecision == null || finalDecision.getAiScore() == null) {
                writer.line(m.get("aiMissing"));
            } else {
                writer.line(m.get("aiJob") + finalDecision.getScoringJob().getId());
                writer.line(m.get("aiScore") + amount(finalDecision.getAiScore()) + " / 100");
                writer.line(m.get("riskCategory") + m.risk(finalDecision.getAiRiskCategory()));
                writer.line(m.get("model") + safeText(finalDecision.getAiModelName()));
                writer.line(m.get("modelVersion") + safeText(finalDecision.getAiModelVersion()));
                writer.line(m.get("recommendedAmount") + amount(finalDecision.getAiRecommendedAmount()) + " KZT");
                writer.line(m.get("shapFactors"));
                for (String factor : shapFactors(finalDecision.getAiTopFactors(), m)) {
                    writer.line("  - " + factor);
                }
                if (finalDecision.getAiLlmSummary() != null && !finalDecision.getAiLlmSummary().isBlank()) {
                    writer.line(m.get("llmConclusion") + finalDecision.getAiLlmSummary());
                }
            }
            writer.blank();

            writer.section(m.get("signatures"));
            writer.line(m.get("signChairman"));
            writer.line(m.get("signSecretary"));
            writer.line(m.get("signMember"));
            writer.close();

            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF protocol generation failed", e);
        }
    }

    private PDFont loadUnicodeFont(PDDocument document) throws IOException {
        List<Path> candidates = new ArrayList<>();
        if (unicodeFontPath != null && !unicodeFontPath.isBlank()) {
            candidates.add(Path.of(unicodeFontPath));
        }
        candidates.addAll(SYSTEM_FONT_CANDIDATES);

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return PDType0Font.load(document, candidate.toFile());
            }
        }
        throw new ServiceUnavailableException(
                "PDF_FONT_NOT_CONFIGURED",
                "Unicode PDF font is not configured. Set APP_PDF_UNICODE_FONT_PATH to a readable TTF font."
        );
    }

    private List<String> shapFactors(String topFactorsJson, ProtocolMessages m) {
        if (topFactorsJson == null || topFactorsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(topFactorsJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            root.forEach(node -> result.add(
                    safeText(node.path("factor").asText(node.path("feature").asText("factor")))
                            + m.get("factorImpact")
                            + m.direction(node.path("direction").asText(node.path("impact").asText("-")))
                            + m.get("factorWeight") + node.path("weight").asText(node.path("shap_value").asText("-"))
            ));
            return result;
        } catch (Exception ignored) {
            return List.of(m.get("factorsUnreadable"));
        }
    }

    private String safeProtocolFileName(String applicationNumber, String language) {
        String safeNumber = safeText(applicationNumber).replaceAll("[^A-Za-z0-9._-]", "_")
                + "-" + language;
        if (safeNumber.isBlank()) {
            safeNumber = UUID.randomUUID().toString();
        }
        return "protocol-" + safeNumber + ".pdf";
    }

    private String maskIinBin(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    private String amount(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value.strip();
    }

    private static class PdfWriter {
        private final ProtocolMessages messages;
        private static final float MARGIN = 45;
        private static final float TOP = 790;
        private static final float BOTTOM = 60;
        private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;

        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        PdfWriter(PDDocument document, PDFont font, ProtocolMessages messages) throws IOException {
            this.messages = messages;
            this.document = document;
            this.font = font;
            newPage();
        }

        void heading(String text) throws IOException {
            write(text, 16, 22);
        }

        void section(String text) throws IOException {
            blank();
            write(text, 13, 18);
        }

        void line(String text) throws IOException {
            write(text, 10, 15);
        }

        void blank() throws IOException {
            ensure(12);
            y -= 8;
        }

        void close() throws IOException {
            closePage();
        }

        private void write(String text, float fontSize, float leading) throws IOException {
            for (String line : wrap(text, fontSize)) {
                ensure(leading);
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(line);
                stream.endText();
                y -= leading;
            }
        }

        private void ensure(float leading) throws IOException {
            if (y - leading < BOTTOM) {
                closePage();
                newPage();
            }
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = TOP;
            pageNumber++;
        }

        private void closePage() throws IOException {
            if (stream == null) {
                return;
            }
            stream.beginText();
            stream.setFont(font, 9);
            stream.newLineAtOffset(MARGIN, 35);
            stream.showText(messages.get("page") + pageNumber);
            stream.endText();
            stream.close();
            stream = null;
        }

        private List<String> wrap(String text, float fontSize) throws IOException {
            String normalized = text == null ? "-" : text.replaceAll("\\s+", " ").strip();
            if (normalized.isBlank()) {
                return List.of("-");
            }
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : normalized.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(candidate, fontSize) <= WIDTH) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (width(word, fontSize) <= WIDTH) {
                    current.append(word);
                } else {
                    lines.addAll(splitLongWord(word, fontSize));
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        private List<String> splitLongWord(String word, float fontSize) throws IOException {
            List<String> result = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int offset = 0; offset < word.length(); ) {
                int codePoint = word.codePointAt(offset);
                String candidate = current.toString() + new String(Character.toChars(codePoint));
                if (width(candidate, fontSize) > WIDTH && !current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                current.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
            }
            if (!current.isEmpty()) {
                result.add(current.toString());
            }
            return result;
        }

        private float width(String value, float fontSize) throws IOException {
            return font.getStringWidth(value) / 1000f * fontSize;
        }
    }
}
