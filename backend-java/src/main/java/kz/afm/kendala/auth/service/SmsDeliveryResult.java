package kz.afm.kendala.auth.service;

public record SmsDeliveryResult(
        SmsDeliveryStatus status,
        String providerMessageId,
        String errorCode
) {
    public static SmsDeliveryResult sent(String providerMessageId) {
        return new SmsDeliveryResult(SmsDeliveryStatus.SENT, providerMessageId, null);
    }

    public static SmsDeliveryResult deliveredLocal(String providerMessageId) {
        return new SmsDeliveryResult(SmsDeliveryStatus.DELIVERED_LOCAL, providerMessageId, null);
    }

    public static SmsDeliveryResult notConfigured(String errorCode) {
        return new SmsDeliveryResult(SmsDeliveryStatus.NOT_CONFIGURED, null, errorCode);
    }

    public static SmsDeliveryResult failed(String errorCode) {
        return new SmsDeliveryResult(SmsDeliveryStatus.FAILED, null, errorCode);
    }
}
