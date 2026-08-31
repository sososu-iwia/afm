package kz.afm.kendala.useradmin;

import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserAccountStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.auth.service.PhoneNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Создаёт первого администратора при развёртывании на сервере.
 *
 * <p>Демо-данные наполняет {@code DevDataSeeder}, но он работает только в профиле dev,
 * а самостоятельная регистрация всегда выдаёт роль «Заявитель». Без этого запуска на
 * production-стенде не осталось бы ни одного пользователя, способного назначать роли.
 *
 * <p>Включается переменной {@code APP_BOOTSTRAP_ADMIN_PHONE}. Запуск идемпотентен:
 * существующий пользователь повышается до администратора, пароль не заводится —
 * вход выполняется по SMS-коду, как и для остальных ролей.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PhoneNormalizer phoneNormalizer;

    public AdminBootstrapRunner(UserRepository userRepository, PhoneNormalizer phoneNormalizer) {
        this.userRepository = userRepository;
        this.phoneNormalizer = phoneNormalizer;
    }

    @Value("${app.bootstrap.admin-phone:}")
    private String adminPhone;

    @Value("${app.bootstrap.admin-name:Администратор системы}")
    private String adminName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminPhone)) {
            return;
        }

        String phone;
        try {
            phone = phoneNormalizer.normalize(adminPhone);
        } catch (RuntimeException exception) {
            log.error("APP_BOOTSTRAP_ADMIN_PHONE содержит некорректный номер, "
                    + "администратор не создан: {}", exception.getMessage());
            return;
        }

        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setFullName(adminName);
            user.setRole(UserRole.ADMIN);
            user.setAccountStatus(UserAccountStatus.ACTIVE);
            userRepository.save(user);
            log.info("Создан администратор {} для первичной настройки системы", maskPhone(phone));
            return;
        }

        boolean changed = false;
        if (user.getRole() != UserRole.ADMIN) {
            user.setRole(UserRole.ADMIN);
            changed = true;
        }
        if (user.getAccountStatus() != UserAccountStatus.ACTIVE) {
            user.setAccountStatus(UserAccountStatus.ACTIVE);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
            log.info("Пользователь {} повышен до администратора", maskPhone(phone));
        }
    }

    /** Номер в журнале хранится в маскированном виде — это персональные данные. */
    private String maskPhone(String phone) {
        return phone.length() <= 4 ? "***" : "***" + phone.substring(phone.length() - 4);
    }
}
