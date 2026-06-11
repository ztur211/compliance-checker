package nz.compliance.app.rules;

import nz.compliance.app.rules.dto.RuleDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/rules")
public class RuleAdminController {

    private final RuleRepository rules;
    private final RuleReviewService review;

    public RuleAdminController(RuleRepository rules, RuleReviewService review) {
        this.rules = rules;
        this.review = review;
    }

    @GetMapping
    public List<RuleDto> drafts() {
        return rules.findByStatus(RuleEntity.RuleStatus.DRAFT).stream().map(RuleDto::from).toList();
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable UUID id) { review.approve(id); }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable UUID id) { review.reject(id); }

    public record EditBody(String parameter, String comparator, double threshold) {}

    @PutMapping("/{id}")
    public void edit(@PathVariable UUID id, @RequestBody EditBody body) {
        review.edit(id, body.parameter(), body.comparator(), body.threshold());
    }

    @PostMapping("/sets/{ruleSetId}/activate")
    public void activate(@PathVariable UUID ruleSetId) { review.activate(ruleSetId); }
}
