package kz.afm.kendala.stage2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.Decision;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.DecisionType;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.DecisionRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class Stage2IntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired UserRepository userRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DecisionRepository decisionRepository;

    // Test data — shared across tests via @BeforeEach
    @Autowired
    jakarta.persistence.EntityManager em;

    private User applicant;
    private Application application;

    @BeforeEach
    void setUp() {
        applicant = new User();
        applicant.setPhone("+7700" + System.nanoTime() % 10000000);
        applicant.setFullName("Test User");
        applicant.setRole(UserRole.APPLICANT);
        userRepository.saveAndFlush(applicant);

        application = new Application();
        application.setApplicationNumber("APP-" + System.nanoTime());
        application.setApplicant(applicant);
        application.setStatus(ApplicationStatus.SUBMITTED);
        em.persist(application);
        em.flush();
    }

    // --- UserRepository.findByPhone ---

    @Test
    void findByPhone_returnsUser() {
        Optional<User> found = userRepository.findByPhone(applicant.getPhone());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(applicant.getId());
    }

    @Test
    void findByPhone_returnsEmptyForUnknown() {
        assertThat(userRepository.findByPhone("+71111111111")).isEmpty();
    }

    // --- Document ---

    @Test
    void saveDocument_persistsAllRequiredFields() {
        Document doc = buildDocument();
        documentRepository.saveAndFlush(doc);

        Document loaded = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(loaded.getApplication().getId()).isEqualTo(application.getId());
        assertThat(loaded.getDocumentType()).isEqualTo(DocumentType.BUSINESS_PLAN);
        assertThat(loaded.getOriginalFileName()).isEqualTo("plan.pdf");
        assertThat(loaded.getStorageKey()).isEqualTo("bucket/plan.pdf");
        assertThat(loaded.getContentType()).isEqualTo("application/pdf");
        assertThat(loaded.getSize()).isEqualTo(4096L);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void findByApplicationId_returnsDocuments() {
        documentRepository.saveAndFlush(buildDocument());

        assertThat(documentRepository.findByApplicationId(application.getId())).hasSize(1);
    }

    @Test
    void document_storageKeyRequired() {
        Document doc = buildDocument();
        doc.setStorageKey(null);

        assertThatThrownBy(() -> documentRepository.saveAndFlush(doc))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Decision ---

    @Test
    void saveDecision_persistsAllRequiredFields() {
        Decision decision = buildDecision(DecisionType.APPROVED, null);
        decisionRepository.saveAndFlush(decision);

        Decision loaded = decisionRepository.findById(decision.getId()).orElseThrow();
        assertThat(loaded.getApplication().getId()).isEqualTo(application.getId());
        assertThat(loaded.getDecidedBy().getId()).isEqualTo(applicant.getId());
        assertThat(loaded.getDecisionType()).isEqualTo(DecisionType.APPROVED);
        assertThat(loaded.getReason()).isNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void saveDecision_withReason() {
        Decision decision = buildDecision(DecisionType.REJECTED, "Недостаточно данных");
        decisionRepository.saveAndFlush(decision);

        Decision loaded = decisionRepository.findById(decision.getId()).orElseThrow();
        assertThat(loaded.getReason()).isEqualTo("Недостаточно данных");
    }

    @Test
    void findByApplicationId_returnsDecisions() {
        decisionRepository.saveAndFlush(buildDecision(DecisionType.ADDITIONAL_DOCUMENTS_REQUESTED, "Нужны документы"));

        assertThat(decisionRepository.findByApplicationId(application.getId())).hasSize(1);
    }

    // --- Helpers ---

    private Document buildDocument() {
        Document doc = new Document();
        doc.setApplication(application);
        doc.setDocumentType(DocumentType.BUSINESS_PLAN);
        doc.setOriginalFileName("plan.pdf");
        doc.setStorageKey("bucket/plan.pdf");
        doc.setContentType("application/pdf");
        doc.setSize(4096L);
        return doc;
    }

    private Decision buildDecision(DecisionType type, String reason) {
        Decision d = new Decision();
        d.setApplication(application);
        d.setDecidedBy(applicant);
        d.setDecisionType(type);
        d.setReason(reason);
        return d;
    }
}
