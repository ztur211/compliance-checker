package nz.compliance.app.rules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ExtractionService {

    private final RuleExtractor extractor;
    private final CandidateValidator validator;
    private final RuleSetRepository ruleSets;
    private final RuleRepository rules;

    public ExtractionService(RuleExtractor extractor, CandidateValidator validator,
                             RuleSetRepository ruleSets, RuleRepository rules) {
        this.extractor = extractor;
        this.validator = validator;
        this.ruleSets = ruleSets;
        this.rules = rules;
    }

    /** Extracts rules from clause texts into a new DRAFT rule set; returns its id. */
    @Transactional
    public UUID extractInto(String name, String version, List<String> clauseTexts) {
        RuleSetEntity rs = ruleSets.save(new RuleSetEntity(name, version));
        for (String clause : clauseTexts) {
            for (RuleCandidate c : extractor.extract(clause)) {
                if (validator.validate(c).isEmpty()) {
                    rules.save(new RuleEntity(rs.getId(), c));
                }
                // invalid candidates are dropped here; a richer UI could surface them for manual fix
            }
        }
        return rs.getId();
    }
}
