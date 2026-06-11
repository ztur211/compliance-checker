package nz.compliance.app.rules.dto;

import nz.compliance.app.rules.RuleEntity;

import java.util.UUID;

public record RuleDto(UUID id, String citation, String parameter, String comparator, double threshold,
                      String status, String sourceQuote, Double confidence) {
    public static RuleDto from(RuleEntity r) {
        return new RuleDto(r.getId(), r.getCitation(), r.getParameter(), r.getComparator(),
                r.getThreshold(), r.getStatus().name(), r.getSourceQuote(), r.getConfidence());
    }
}
