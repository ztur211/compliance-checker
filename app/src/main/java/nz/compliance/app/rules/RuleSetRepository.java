package nz.compliance.app.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RuleSetRepository extends JpaRepository<RuleSetEntity, UUID> {
    Optional<RuleSetEntity> findFirstByActiveTrueOrderByCreatedAtDesc();
}
