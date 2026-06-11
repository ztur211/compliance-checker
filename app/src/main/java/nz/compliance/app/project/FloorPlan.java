package nz.compliance.app.project;

import jakarta.persistence.*;
import nz.compliance.engine.model.GeometryDoc;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "floor_plans")
public class FloorPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int level = 0;

    @Column(name = "risk_group")
    private String riskGroup;

    private Boolean sprinklered;

    @Column(name = "escape_height_metres")
    private Double escapeHeightMetres;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geometry_json", nullable = false, columnDefinition = "jsonb")
    private GeometryDoc geometry = new GeometryDoc(1, List.of(), List.of());

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected FloorPlan() {
    }

    public FloorPlan(UUID projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getRiskGroup() { return riskGroup; }
    public void setRiskGroup(String riskGroup) { this.riskGroup = riskGroup; }
    public Boolean getSprinklered() { return sprinklered; }
    public void setSprinklered(Boolean sprinklered) { this.sprinklered = sprinklered; }
    public Double getEscapeHeightMetres() { return escapeHeightMetres; }
    public void setEscapeHeightMetres(Double v) { this.escapeHeightMetres = v; }
    public GeometryDoc getGeometry() { return geometry; }
    public void setGeometry(GeometryDoc geometry) { this.geometry = geometry; }
}
