package nz.compliance.app.rules;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "rules")
public class RuleEntity {
    public enum RuleStatus { DRAFT, APPROVED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "rule_set_id", nullable = false) private UUID ruleSetId;
    @Column(nullable = false) private String citation;
    private String title;
    @Column(nullable = false) private String parameter;
    @Column(nullable = false) private String comparator;
    @Column(nullable = false) private double threshold;
    @Column(nullable = false) private String severity = "ERROR";
    @Column(name = "risk_groups") private String riskGroups = "";
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RuleStatus status = RuleStatus.DRAFT;
    @Column(name = "source_quote") private String sourceQuote;
    private Double confidence;

    protected RuleEntity() {}

    public RuleEntity(UUID ruleSetId, RuleCandidate c) {
        this.ruleSetId = ruleSetId;
        this.citation = c.citation();
        this.title = c.title();
        this.parameter = c.parameter();
        this.comparator = c.comparator();
        this.threshold = c.threshold();
        this.riskGroups = String.join(",", c.riskGroups());
        this.sourceQuote = c.sourceQuote();
        this.confidence = c.confidence();
    }

    public UUID getId() { return id; }
    public UUID getRuleSetId() { return ruleSetId; }
    public String getCitation() { return citation; }
    public String getTitle() { return title; }
    public String getParameter() { return parameter; }
    public String getComparator() { return comparator; }
    public double getThreshold() { return threshold; }
    public String getSeverity() { return severity; }
    public String getRiskGroups() { return riskGroups; }
    public RuleStatus getStatus() { return status; }
    public void setStatus(RuleStatus s) { this.status = s; }
    public String getSourceQuote() { return sourceQuote; }
    public Double getConfidence() { return confidence; }
    public void setThreshold(double t) { this.threshold = t; }
    public void setComparator(String c) { this.comparator = c; }
    public void setParameter(String p) { this.parameter = p; }
}
