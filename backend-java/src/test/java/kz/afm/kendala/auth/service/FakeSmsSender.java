package kz.afm.kendala.auth.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Test-only SMS fixture; deliberately absent from src/main. */
@Component
@Primary
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "fake")
public class FakeSmsSender implements SmsSender {

    private final ConcurrentHashMap<String, String> sentMessages = new ConcurrentHashMap<>();

    @Override
    public SmsDeliveryResult send(String to, String message) {
        sentMessages.put(to, message);
        return SmsDeliveryResult.sent("test-message");
    }

    public String getLastMessage(String phone) {
        return sentMessages.get(phone);
    }

    public String getLastCode(String phone) {
        String message = sentMessages.get(phone);
        return message == null ? null : message.replaceAll("\\D", "");
    }
}
