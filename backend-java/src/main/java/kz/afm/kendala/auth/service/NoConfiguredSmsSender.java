package kz.afm.kendala.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(SmsSender.class)
public class NoConfiguredSmsSender implements SmsSender {

    @Override
    public SmsDeliveryResult send(String to, String message) {
        return SmsDeliveryResult.notConfigured("SMS_NOT_CONFIGURED");
    }
}
