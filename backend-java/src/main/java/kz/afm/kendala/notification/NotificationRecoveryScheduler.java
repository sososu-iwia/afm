package kz.afm.kendala.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationRecoveryScheduler {
    private final NotificationDeliveryStateService stateService;
    private final NotificationWorker worker;

    public NotificationRecoveryScheduler(
            NotificationDeliveryStateService stateService,
            NotificationWorker worker
    ) {
        this.stateService = stateService;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.notifications.recovery-delay-ms:30000}")
    public void recover() {
        stateService.recoverAndFindPending().forEach(worker::processPending);
    }
}
