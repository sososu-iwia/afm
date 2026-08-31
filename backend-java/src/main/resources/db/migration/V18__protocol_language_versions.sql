-- Протокол выпускается на языке получателя (ru/kz/en). Раньше на заявку
-- допускался ровно один протокол, поэтому вторая языковая версия падала на
-- уникальном индексе. Теперь уникальна пара «заявка + язык».

ALTER TABLE generated_protocols
    ADD COLUMN language VARCHAR(8) NOT NULL DEFAULT 'ru';

DROP INDEX IF EXISTS uq_generated_protocol_application;

CREATE UNIQUE INDEX uq_generated_protocol_application_language
    ON generated_protocols(application_id, language);
