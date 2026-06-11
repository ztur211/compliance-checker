package nz.compliance.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for Postgres-backed integration tests.
 *
 * <p>Uses the Testcontainers "singleton container" pattern: a single container
 * is started once in a static initializer and shared across every {@code *IT}
 * subclass (and every cached Spring context) for the whole test run. Ryuk stops
 * it at JVM shutdown. This is deliberately NOT the {@code @Testcontainers}/
 * {@code @Container} per-class lifecycle, which stops/restarts the container
 * between classes and breaks cached Spring contexts whose datasource still
 * points at the old (now-stopped) container port.
 *
 * <p>Because the database is shared and accumulates rows across classes,
 * integration tests must keep their assertions isolation-safe (scope them to
 * ids/entities they created, not to global ordering or counts).
 */
@SpringBootTest
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Tests run the check job inline and share a single JVM across IT classes;
        // keep JobRunr's background worker and (port-8080/8000) dashboard out of the
        // way so cached Spring contexts don't collide on the dashboard port.
        registry.add("org.jobrunr.background-job-server.enabled", () -> "false");
        registry.add("org.jobrunr.dashboard.enabled", () -> "false");
    }
}
