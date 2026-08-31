package kz.afm.kendala.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class OpenApiConfig {

    @Bean
    public OpenAPI kenDalaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Кең дала 2 API")
                        .version("0.1.0")
                        .description("Backend API системы автоматизации подачи и рассмотрения заявок по программе «Кең дала 2»"))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ))
                .tags(List.of(
                        new Tag().name("Authentication").description("Регистрация, OTP и JWT-сессии"),
                        new Tag().name("Applications").description("Заявки заявителя"),
                        new Tag().name("Documents").description("Защищённые документы заявок"),
                        new Tag().name("Application Processing").description("Текущая ревизия OCR и AI-обработки"),
                        new Tag().name("Commission").description("Рассмотрение заявок комиссией"),
                        new Tag().name("Analytics").description("Агрегированная аналитика"),
                        new Tag().name("Public Registry").description("Публичный реестр одобренных заявок"),
                        new Tag().name("User Administration").description("Администрирование пользователей"),
                        new Tag().name("Audit").description("Журнал аудита"),
                        new Tag().name("AI Jobs").description("Диагностика и безопасный retry AI jobs"),
                        new Tag().name("Health").description("Проверка состояния сервиса")
                ));
    }
}
