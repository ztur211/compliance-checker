package nz.compliance.app.check;

import jakarta.persistence.*;
import nz.compliance.engine.check.CheckResult;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "check_runs")
public class CheckRun {

    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "floor_plan_id", nullable = false)
    private UUID floorPlanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.QUEUED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private CheckResult result;

    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected CheckRun() {
    }

    public CheckRun(UUID floorPlanId) {
        this.floorPlanId = floorPlanId;
    }

    public UUID getId() { return id; }
    public UUID getFloorPlanId() { return floorPlanId; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }
    public CheckResult getResult() { return result; }
    public void setResult(CheckResult r) { this.result = r; }
    public String getError() { return error; }
    public void setError(String e) { this.error = e; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant t) { this.finishedAt = t; }
}
