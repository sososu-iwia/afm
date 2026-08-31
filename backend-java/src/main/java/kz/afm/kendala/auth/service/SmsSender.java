package kz.afm.kendala.auth.service;

public interface SmsSender {

    SmsDeliveryResult send(String to, String message);
}
