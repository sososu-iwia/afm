package kz.afm.kendala.ai;

import java.util.Locale;
import java.util.Map;

/**
 * Подписи протокола заседания на трёх языках.
 *
 * Перечисления печатаются словами, а не константами кода: протокол — официальный
 * документ, и в нём не должно быть CHAIRMAN или APPROVED.
 */
public final class ProtocolMessages {

    public static final String RU = "ru";
    public static final String KZ = "kz";
    public static final String EN = "en";

    private static final Map<String, Map<String, String>> LABELS = Map.of(
            RU, Map.ofEntries(
                    Map.entry("title", "Протокол заседания кредитной комиссии"),
                    Map.entry("programme", "Программа льготного кредитования «Кең дала 2»"),
                    Map.entry("meetingNumber", "Номер заседания: "),
                    Map.entry("meetingDate", "Дата заседания: "),
                    Map.entry("generatedBy", "Сформировал: "),
                    Map.entry("committee", "Данные комиссии"),
                    Map.entry("chairman", "Председатель комиссии"),
                    Map.entry("secretary", "Секретарь комиссии"),
                    Map.entry("members", "Члены комиссии: член комиссии, председатель комиссии, секретарь комиссии"),
                    Map.entry("applicationsSection", "Список рассмотренных заявок"),
                    Map.entry("application", "Заявка: "),
                    Map.entry("revision", "Ревизия подачи: "),
                    Map.entry("applicant", "Заявитель: "),
                    Map.entry("iinBin", "ИИН/БИН: "),
                    Map.entry("region", "Регион: "),
                    Map.entry("activity", "Вид деятельности: "),
                    Map.entry("category", "Категория заявителя: "),
                    Map.entry("product", "Продукция: "),
                    Map.entry("area", "Площадь: "),
                    Map.entry("areaUnit", " га"),
                    Map.entry("requestedAmount", "Запрашиваемая сумма: "),
                    Map.entry("approvedAmount", "Одобренная сумма: "),
                    Map.entry("decisionSection", "Решение комиссии"),
                    Map.entry("decision", "Решение: "),
                    Map.entry("decisionDate", "Дата решения: "),
                    Map.entry("decidedBy", "Принял: "),
                    Map.entry("reason", "Причина/комментарий: "),
                    Map.entry("aiSection", "ИИ-скоринг и объяснение"),
                    Map.entry("aiMissing", "Оценка ИИ: не зафиксирована в решении"),
                    Map.entry("aiJob", "Задача ИИ: "),
                    Map.entry("aiScore", "Оценка ИИ: "),
                    Map.entry("riskCategory", "Категория риска: "),
                    Map.entry("model", "Модель: "),
                    Map.entry("modelVersion", "Версия модели: "),
                    Map.entry("recommendedAmount", "Рекомендованная сумма: "),
                    Map.entry("shapFactors", "Важные SHAP-факторы:"),
                    Map.entry("llmConclusion", "Заключение ИИ: "),
                    Map.entry("signatures", "Блок подписей"),
                    Map.entry("signChairman", "Председатель комиссии: ______________________________"),
                    Map.entry("signSecretary", "Секретарь комиссии: _________________________________"),
                    Map.entry("signMember", "Член комиссии: ______________________________________"),
                    Map.entry("page", "Страница "),
                    Map.entry("factorImpact", " — влияние: "),
                    Map.entry("factorWeight", ", вес: "),
                    Map.entry("factorsUnreadable", "Факторы SHAP не удалось прочитать"),
                    Map.entry("notSet", "не указано")
            ),
            KZ, Map.ofEntries(
                    Map.entry("title", "Кредиттік комиссия отырысының хаттамасы"),
                    Map.entry("programme", "«Кең дала 2» жеңілдікті несиелеу бағдарламасы"),
                    Map.entry("meetingNumber", "Отырыс нөмірі: "),
                    Map.entry("meetingDate", "Отырыс күні: "),
                    Map.entry("generatedBy", "Қалыптастырған: "),
                    Map.entry("committee", "Комиссия туралы мәліметтер"),
                    Map.entry("chairman", "Комиссия төрағасы"),
                    Map.entry("secretary", "Комиссия хатшысы"),
                    Map.entry("members", "Комиссия мүшелері: комиссия мүшесі, комиссия төрағасы, комиссия хатшысы"),
                    Map.entry("applicationsSection", "Қаралған өтінімдер тізімі"),
                    Map.entry("application", "Өтінім: "),
                    Map.entry("revision", "Тапсыру нұсқасы: "),
                    Map.entry("applicant", "Өтініш беруші: "),
                    Map.entry("iinBin", "ЖСН/БСН: "),
                    Map.entry("region", "Өңір: "),
                    Map.entry("activity", "Қызмет түрі: "),
                    Map.entry("category", "Өтініш беруші санаты: "),
                    Map.entry("product", "Өнім: "),
                    Map.entry("area", "Алаңы: "),
                    Map.entry("areaUnit", " га"),
                    Map.entry("requestedAmount", "Сұралған сома: "),
                    Map.entry("approvedAmount", "Мақұлданған сома: "),
                    Map.entry("decisionSection", "Комиссия шешімі"),
                    Map.entry("decision", "Шешім: "),
                    Map.entry("decisionDate", "Шешім күні: "),
                    Map.entry("decidedBy", "Қабылдаған: "),
                    Map.entry("reason", "Себебі/түсініктеме: "),
                    Map.entry("aiSection", "ЖИ-скоринг және түсіндірме"),
                    Map.entry("aiMissing", "ЖИ бағасы: шешімде тіркелмеген"),
                    Map.entry("aiJob", "ЖИ тапсырмасы: "),
                    Map.entry("aiScore", "ЖИ бағасы: "),
                    Map.entry("riskCategory", "Тәуекел санаты: "),
                    Map.entry("model", "Модель: "),
                    Map.entry("modelVersion", "Модель нұсқасы: "),
                    Map.entry("recommendedAmount", "Ұсынылған сома: "),
                    Map.entry("shapFactors", "Маңызды SHAP факторлары:"),
                    Map.entry("llmConclusion", "ЖИ қорытындысы: "),
                    Map.entry("signatures", "Қолдар блогы"),
                    Map.entry("signChairman", "Комиссия төрағасы: __________________________________"),
                    Map.entry("signSecretary", "Комиссия хатшысы: __________________________________"),
                    Map.entry("signMember", "Комиссия мүшесі: ___________________________________"),
                    Map.entry("page", "Бет "),
                    Map.entry("factorImpact", " — әсері: "),
                    Map.entry("factorWeight", ", салмағы: "),
                    Map.entry("factorsUnreadable", "SHAP факторларын оқу мүмкін болмады"),
                    Map.entry("notSet", "көрсетілмеген")
            ),
            EN, Map.ofEntries(
                    Map.entry("title", "Minutes of the credit committee meeting"),
                    Map.entry("programme", "«Ken Dala 2» subsidised lending programme"),
                    Map.entry("meetingNumber", "Meeting number: "),
                    Map.entry("meetingDate", "Meeting date: "),
                    Map.entry("generatedBy", "Prepared by: "),
                    Map.entry("committee", "Committee details"),
                    Map.entry("chairman", "Committee chairman"),
                    Map.entry("secretary", "Committee secretary"),
                    Map.entry("members", "Committee members: committee member, chairman, secretary"),
                    Map.entry("applicationsSection", "Applications reviewed"),
                    Map.entry("application", "Application: "),
                    Map.entry("revision", "Submission revision: "),
                    Map.entry("applicant", "Applicant: "),
                    Map.entry("iinBin", "IIN/BIN: "),
                    Map.entry("region", "Region: "),
                    Map.entry("activity", "Activity type: "),
                    Map.entry("category", "Applicant category: "),
                    Map.entry("product", "Product: "),
                    Map.entry("area", "Area: "),
                    Map.entry("areaUnit", " ha"),
                    Map.entry("requestedAmount", "Requested amount: "),
                    Map.entry("approvedAmount", "Approved amount: "),
                    Map.entry("decisionSection", "Committee decision"),
                    Map.entry("decision", "Decision: "),
                    Map.entry("decisionDate", "Decision date: "),
                    Map.entry("decidedBy", "Decided by: "),
                    Map.entry("reason", "Reason/comment: "),
                    Map.entry("aiSection", "AI scoring and explanation"),
                    Map.entry("aiMissing", "AI score: not recorded in the decision"),
                    Map.entry("aiJob", "AI job: "),
                    Map.entry("aiScore", "AI score: "),
                    Map.entry("riskCategory", "Risk category: "),
                    Map.entry("model", "Model: "),
                    Map.entry("modelVersion", "Model version: "),
                    Map.entry("recommendedAmount", "Recommended amount: "),
                    Map.entry("shapFactors", "Key SHAP factors:"),
                    Map.entry("llmConclusion", "AI conclusion: "),
                    Map.entry("signatures", "Signatures"),
                    Map.entry("signChairman", "Committee chairman: _________________________________"),
                    Map.entry("signSecretary", "Committee secretary: ________________________________"),
                    Map.entry("signMember", "Committee member: ___________________________________"),
                    Map.entry("page", "Page "),
                    Map.entry("factorImpact", " — impact: "),
                    Map.entry("factorWeight", ", weight: "),
                    Map.entry("factorsUnreadable", "SHAP factors could not be read"),
                    Map.entry("notSet", "not specified")
            )
    );

    private static final Map<String, Map<String, String>> ROLES = Map.of(
            RU, Map.of("CHAIRMAN", "председатель комиссии", "SECRETARY", "секретарь комиссии",
                    "COMMISSION_MEMBER", "член комиссии", "APPLICANT", "заявитель",
                    "ADMIN", "администратор", "MANAGER", "менеджер"),
            KZ, Map.of("CHAIRMAN", "комиссия төрағасы", "SECRETARY", "комиссия хатшысы",
                    "COMMISSION_MEMBER", "комиссия мүшесі", "APPLICANT", "өтініш беруші",
                    "ADMIN", "әкімші", "MANAGER", "менеджер"),
            EN, Map.of("CHAIRMAN", "chairman", "SECRETARY", "secretary",
                    "COMMISSION_MEMBER", "committee member", "APPLICANT", "applicant",
                    "ADMIN", "administrator", "MANAGER", "manager")
    );

    private static final Map<String, Map<String, String>> ACTIVITIES = Map.of(
            RU, Map.of("CROP_PRODUCTION", "растениеводство", "LIVESTOCK_PRODUCTION", "животноводство",
                    "PROCESSING", "переработка", "STORAGE", "хранение", "OTHER", "иное"),
            KZ, Map.of("CROP_PRODUCTION", "өсімдік шаруашылығы", "LIVESTOCK_PRODUCTION", "мал шаруашылығы",
                    "PROCESSING", "қайта өңдеу", "STORAGE", "сақтау", "OTHER", "өзге"),
            EN, Map.of("CROP_PRODUCTION", "crop production", "LIVESTOCK_PRODUCTION", "livestock production",
                    "PROCESSING", "processing", "STORAGE", "storage", "OTHER", "other")
    );

    private static final Map<String, Map<String, String>> CATEGORIES = Map.of(
            RU, Map.of("INDIVIDUAL_ENTREPRENEUR", "индивидуальный предприниматель",
                    "LEGAL_ENTITY", "юридическое лицо", "PEASANT_FARM", "крестьянское хозяйство",
                    "COOPERATIVE", "кооператив", "OTHER", "иное"),
            KZ, Map.of("INDIVIDUAL_ENTREPRENEUR", "жеке кәсіпкер",
                    "LEGAL_ENTITY", "заңды тұлға", "PEASANT_FARM", "шаруа қожалығы",
                    "COOPERATIVE", "кооператив", "OTHER", "өзге"),
            EN, Map.of("INDIVIDUAL_ENTREPRENEUR", "individual entrepreneur",
                    "LEGAL_ENTITY", "legal entity", "PEASANT_FARM", "peasant farm",
                    "COOPERATIVE", "cooperative", "OTHER", "other")
    );

    private static final Map<String, Map<String, String>> DECISIONS = Map.of(
            RU, Map.of("APPROVED", "одобрено", "REJECTED", "отказано",
                    "ADDITIONAL_DOCUMENTS_REQUESTED", "запрошены дополнительные документы",
                    "SUBMITTED", "на рассмотрении", "IN_REVIEW", "на рассмотрении",
                    "DRAFT", "черновик", "WITHDRAWN", "отозвано"),
            KZ, Map.of("APPROVED", "мақұлданды", "REJECTED", "бас тартылды",
                    "ADDITIONAL_DOCUMENTS_REQUESTED", "қосымша құжаттар сұралды",
                    "SUBMITTED", "қаралуда", "IN_REVIEW", "қаралуда",
                    "DRAFT", "жоба", "WITHDRAWN", "кері қайтарылды"),
            EN, Map.of("APPROVED", "approved", "REJECTED", "rejected",
                    "ADDITIONAL_DOCUMENTS_REQUESTED", "additional documents requested",
                    "SUBMITTED", "under review", "IN_REVIEW", "under review",
                    "DRAFT", "draft", "WITHDRAWN", "withdrawn")
    );

    private static final Map<String, Map<String, String>> RISKS = Map.of(
            RU, Map.of("LOW", "низкий", "MEDIUM", "средний", "HIGH", "высокий"),
            KZ, Map.of("LOW", "төмен", "MEDIUM", "орташа", "HIGH", "жоғары"),
            EN, Map.of("LOW", "low", "MEDIUM", "medium", "HIGH", "high")
    );

    private static final Map<String, Map<String, String>> DIRECTIONS = Map.of(
            RU, Map.of("positive", "положительное", "negative", "отрицательное", "neutral", "нейтральное"),
            KZ, Map.of("positive", "оң", "negative", "теріс", "neutral", "бейтарап"),
            EN, Map.of("positive", "positive", "negative", "negative", "neutral", "neutral")
    );

    private final String language;

    private ProtocolMessages(String language) {
        this.language = language;
    }

    /** Неизвестный или пустой язык откатывается на русский. */
    public static ProtocolMessages of(String language) {
        String normalized = language == null ? RU : language.toLowerCase(Locale.ROOT);
        return new ProtocolMessages(LABELS.containsKey(normalized) ? normalized : RU);
    }

    public String language() {
        return language;
    }

    public String get(String key) {
        return LABELS.get(language).getOrDefault(key, key);
    }

    public String role(Object value) {
        return lookup(ROLES, value);
    }

    public String activity(Object value) {
        return lookup(ACTIVITIES, value);
    }

    public String category(Object value) {
        return lookup(CATEGORIES, value);
    }

    public String decision(Object value) {
        return lookup(DECISIONS, value);
    }

    public String risk(Object value) {
        return lookup(RISKS, value);
    }

    public String direction(Object value) {
        if (value == null) {
            return get("notSet");
        }
        String key = value.toString();
        Map<String, String> table = DIRECTIONS.get(language);
        String direct = table.get(key);
        if (direct != null) {
            return direct;
        }
        // Направление приходит и в верхнем регистре (POSITIVE/NEGATIVE).
        return table.getOrDefault(key.toLowerCase(Locale.ROOT), key);
    }

    private String lookup(Map<String, Map<String, String>> table, Object value) {
        if (value == null) {
            return get("notSet");
        }
        String key = value.toString();
        return table.get(language).getOrDefault(key, key);
    }
}
