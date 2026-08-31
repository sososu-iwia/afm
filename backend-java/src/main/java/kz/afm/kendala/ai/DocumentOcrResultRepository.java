package kz.afm.kendala.ai;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentOcrResultRepository extends JpaRepository<DocumentOcrResult, UUID> {
}
