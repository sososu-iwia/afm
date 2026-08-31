package kz.afm.kendala.application.repository;

import java.util.Optional;
import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByPhoneAndActiveTrue(String phone);
    Optional<User> findByEmailIgnoreCase(String email);
}
