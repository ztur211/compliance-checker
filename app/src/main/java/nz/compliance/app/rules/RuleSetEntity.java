package nz.compliance.app.rules;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rule_sets")
public class RuleSetEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String version;
    @Column(nullable = false) private boolean active = false;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected RuleSetEntity() {}
    public RuleSetEntity(String name, String version) { this.name = name; this.version = version; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
