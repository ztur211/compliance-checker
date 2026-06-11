package nz.compliance.app.check;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CheckRunRepository extends JpaRepository<CheckRun, UUID> {
}
