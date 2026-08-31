package kz.afm.kendala.application.repository;

import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.entity.DocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, UUID> {

    @Query("""
            select r from DocumentRequirement r
            where r.applicantType = :applicantType
              and r.active = true
              and r.requirementVersion = (
                  select max(r2.requirementVersion) from DocumentRequirement r2
                  where r2.applicantType = :applicantType and r2.active = true
              )
            """)
    List<DocumentRequirement> findCurrentActive(@Param("applicantType") String applicantType);
}
