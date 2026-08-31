package kz.afm.kendala.dev;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.ai.AiProcessingJob;
import kz.afm.kendala.ai.AiProcessingJobRepository;
import kz.afm.kendala.ai.AiOrchestrationService;
import kz.afm.kendala.ai.AiTaskStatus;
import kz.afm.kendala.ai.GeneratedProtocol;
import kz.afm.kendala.ai.GeneratedProtocolRepository;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.application.service.ApplicationCompletenessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final AiProcessingJobRepository aiJobRepository;
    private final GeneratedProtocolRepository protocolRepository;
    private final ApplicationRepository applicationRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final ApplicationCompletenessService completenessService;

    public DevDataSeeder(
            UserRepository userRepository,
            ApplicationRepository applicationRepository,
            AiProcessingJobRepository aiJobRepository,
            GeneratedProtocolRepository protocolRepository,
            AiOrchestrationService aiOrchestrationService,
            ApplicationCompletenessService completenessService
    ) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.aiJobRepository = aiJobRepository;
        this.protocolRepository = protocolRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.completenessService = completenessService;
    }

    @Override
    public void run(ApplicationArguments args) {
        User applicant = seedUser(UUID.fromString("11111111-1111-1111-1111-111111111111"), "+77000000001", "ТОО «Агро Дала»", UserRole.APPLICANT);
        seedUser(UUID.fromString("22222222-2222-2222-2222-222222222222"), "+77000000002", "Томирис Жусупова", UserRole.CHAIRMAN);
        seedUser(UUID.fromString("33333333-3333-3333-3333-333333333333"), "+77000000003", "Марат Жумабеков", UserRole.COMMISSION_MEMBER);
        seedUser(UUID.fromString("44444444-4444-4444-4444-444444444444"), "+77000000004", "Динара Омарова", UserRole.SECRETARY);
        seedUser(UUID.fromString("55555555-5555-5555-5555-555555555555"), "+77000000005", "Асель Каримова", UserRole.ADMIN);

        seedApplication("KD2-DEMO-001", applicant, ApplicationStatus.DRAFT, "Костанайская область", "Пшеница", "123456789012", 240, 18_500_000);
        seedApplication("KD2-DEMO-002", applicant, ApplicationStatus.SUBMITTED, "Акмолинская область", "Масличные культуры", "123456789012", 420, 31_200_000);
        seedApplication("KD2-DEMO-003", applicant, ApplicationStatus.IN_REVIEW, "Северо-Казахстанская область", "Молочная продукция", "123456789012", 180, 24_800_000);
        seedApplication("KD2-DEMO-004", applicant, ApplicationStatus.APPROVED, "Костанайская область", "Овощи", "123456789012", 95, 12_400_000);
        seedApplication("KD2-DEMO-005", applicant, ApplicationStatus.REJECTED, "Павлодарская область", "Ячмень", "123456789012", 130, 16_700_000);
    }

    private User seedUser(UUID id, String phone, String fullName, UserRole role) {
        User user = userRepository.findById(id).orElseGet(() -> {
            User created = new User();
            created.setId(id);
            return created;
        });
        user.setPhone(phone);
        user.setFullName(fullName);
        user.setRole(role);
        return userRepository.save(user);
    }

    private void seedApplication(String number, User applicant, ApplicationStatus status, String region,
                                 String productionType, String iinOrBin, double landArea, long amount) {
        Application application = applicationRepository.findByApplicationNumber(number).orElseGet(() -> {
            Application created = new Application();
            created.setApplicationNumber(number);
            created.setApplicant(applicant);
            return created;
        });
        application.setStatus(status);
        application.setRegion(region);
        application.setProductionType(productionType);
        application.setIinOrBin(iinOrBin);
        application.setLandArea(BigDecimal.valueOf(landArea));
        application.setRequestedAmount(BigDecimal.valueOf(amount));
        application.setSubmissionRevision(status == ApplicationStatus.DRAFT ? 0 : 1);
        if (status == ApplicationStatus.APPROVED) {
            application.setApprovedAmount(BigDecimal.valueOf(amount));
            if (application.getDecisionAt() == null) {
                application.setDecisionAt(Instant.now());
            }
            // Demo data ships pre-published so the public registry is not empty on a fresh start.
            if (!application.isPublicVisible()) {
                application.setPublicVisible(true);
                application.setPublishedAt(Instant.now());
            }
        }
        Application saved = applicationRepository.save(application);
        clearFailedAiJobs(saved);
        clearStaleProtocols(saved);
        startAiWorkflow(saved, applicant);
    }

    /**
     * Демо-заявки создаются сразу в конечных статусах, поэтому обычный путь
     * «подача → AI-конвейер» их не задевал: карточка открывалась без скоринга
     * и без завершённой проверки дублей. Запускаем конвейер явно для тех
     * статусов, для которых его допускает сервер.
     */
    private void startAiWorkflow(Application application, User requestedBy) {
        if (application.getStatus() != ApplicationStatus.SUBMITTED
                && application.getStatus() != ApplicationStatus.IN_REVIEW) {
            return;
        }
        try {
            aiOrchestrationService.startAutomaticWorkflow(
                    application, requestedBy, completenessService.check(application));
        } catch (Exception exception) {
            // Недоступный AI-сервис не должен мешать запуску приложения:
            // задачи останутся в очереди и обработаются воркером позже.
            log.warn("Не удалось поставить AI-задачи для демо-заявки {}: {}",
                    application.getApplicationNumber(), exception.getMessage());
        }
    }

    /**
     * Demo applications are rewritten on every start, so AI jobs that failed against
     * their previous field values would otherwise keep showing an error on the
     * commission screen. Drop them and let the workflow score the current data.
     */
    /**
     * Протокол кэшируется навсегда, поэтому демо-протоколы, собранные по прежним
     * значениям (старые типы производства, англоязычные подписи), пришлось бы
     * показывать на демонстрации как есть. Сбрасываем их вместе с данными.
     */
    private void clearStaleProtocols(Application application) {
        List<GeneratedProtocol> protocols =
                protocolRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId());
        if (!protocols.isEmpty()) {
            protocolRepository.deleteAll(protocols);
        }
    }

    private void clearFailedAiJobs(Application application) {
        List<AiProcessingJob> failed = aiJobRepository
                .findByApplicationIdOrderByCreatedAtAsc(application.getId())
                .stream()
                .filter(job -> job.getStatus() == AiTaskStatus.FAILED_TERMINAL
                        || job.getStatus() == AiTaskStatus.FAILED_OPTIONAL)
                .toList();
        if (!failed.isEmpty()) {
            aiJobRepository.deleteAll(failed);
        }
    }
}
