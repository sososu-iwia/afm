package kz.afm.kendala.auth.service;

import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private final SmsSender smsSender;

    public SmsService(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    public SmsDeliveryResult sendOtp(String phone, String code) {
        return smsSender.send(phone, "Код подтверждения: " + code + ". Не сообщайте его никому.");
    }
}
