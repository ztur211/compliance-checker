# Rules, Resolution & End-to-End Check Implementation Plan (Plan 5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the engine with a data-driven rule model + evaluator (`ComplianceEngine.check`), then wire it into the app behind an async **JobRunr** `CheckRun` and render located violations in the frontend — delivering the first **draw → Check → see violations** flow.

**Architecture:** Rules are data (`{parameter, comparator, threshold, riskGroups}`). The engine computes `PlanFacts` + `EgressResult` once, resolves the applicable rules for the `BuildingContext`, reads each rule's value through a `ParameterRegistry`, and emits `Violation`s (located on a space + egress path) / `passed` / `notEvaluated`, plus a structural "no means of escape" violation and a "no exits" block. The app stores the result as jsonb on a `CheckRun`, runs the check in a JobRunr worker, and the frontend polls and highlights. For v1 the RuleSet is **hand-authored** in code (`DefaultNzEgressRuleSet`); Plan 6 replaces it with LLM-codified, DB-stored rules. **Units: metres, mm.**

**Tech Stack:** engine (JTS + JGraphT); Spring Boot + JobRunr; React/TS viewer.

**Prerequisite:** Plan 4 complete (`FactsComputer`, `EgressAnalyzer`).

---

## File map

```
engine/src/main/java/nz/compliance/engine/model/BuildingContext.java
engine/src/main/java/nz/compliance/engine/rules/{Scope,ParameterKey,Comparator,Severity}.java
engine/src/main/java/nz/compliance/engine/rules/{Rule,RuleSet}.java
engine/src/main/java/nz/compliance/engine/check/{ParameterRegistry,Violation,OutcomeStatus,RuleOutcome,CheckResult,ComplianceEngine}.java
engine/src/test/java/nz/compliance/engine/check/ComplianceEngineTest.java

app/pom.xml                                                  # + jobrunr
app/src/main/resources/db/migration/V2__check_runs.sql
app/src/main/java/nz/compliance/app/check/{CheckRun,CheckRunRepository}.java
app/src/main/java/nz/compliance/app/check/DefaultNzEgressRuleSet.java
app/src/main/java/nz/compliance/app/check/CheckService.java
app/src/main/java/nz/compliance/app/check/CheckJob.java
app/src/main/java/nz/compliance/app/check/CheckController.java
app/src/main/java/nz/compliance/app/check/dto/CheckRunDto.java
app/src/test/java/nz/compliance/app/check/CheckFlowIT.java

frontend/src/api/checks.ts
frontend/src/editor/ResultsPanel.tsx
frontend/src/editor/EditorPage.tsx                            # + Check button, polling, highlight
frontend/src/editor/EditorCanvas.tsx                          # + highlighted spaces/path props
```

---

## Task 1: Rule model (data)

**Files:**
- Create: `engine/src/main/java/nz/compliance/engine/model/BuildingContext.java`
- Create: `engine/src/main/java/nz/compliance/engine/rules/Scope.java`
- Create: `engine/src/main/java/nz/compliance/engine/rules/ParameterKey.java`
- Create: `engine/src/main/java/nz/compliance/engine/rules/Comparator.java`
- Create: `engine/src/main/java/nz/compliance/engine/rules/Severity.java`
- Create: `engine/src/main/java/nz/compliance/engine/rules/Rule.java`
- Create: `engine/src/main/java/nz/compliance/engine/rules/RuleSet.java`
- Test: `engine/src/test/java/nz/compliance/engine/rules/RuleSetTest.java`

- [ ] **Step 1: Create the enums + context**

`engine/src/main/java/nz/compliance/engine/model/BuildingContext.java`:
```java
package nz.compliance.engine.model;

/** Building characteristics that drive rule applicability and thresholds (NZ). */
public record BuildingContext(String riskGroup, boolean sprinklered, Double escapeHeightMetres) {
}
```

`engine/src/main/java/nz/compliance/engine/rules/Scope.java`:
```java
package nz.compliance.engine.rules;

public enum Scope { PER_SPACE, WHOLE_PLAN }
```

`engine/src/main/java/nz/compliance/engine/rules/ParameterKey.java`:
```java
package nz.compliance.engine.rules;

/** A checkable quantity the engine can extract. Add an extractor in ParameterRegistry when adding one. */
public enum ParameterKey {
    OPEN_PATH_LENGTH(Scope.PER_SPACE),
    OCCUPANT_LOAD(Scope.PER_SPACE),
    EXIT_COUNT(Scope.WHOLE_PLAN),
    EXIT_WIDTH(Scope.WHOLE_PLAN);

    private final Scope scope;

    ParameterKey(Scope scope) { this.scope = scope; }

    public Scope scope() { return scope; }
}
```

`engine/src/main/java/nz/compliance/engine/rules/Comparator.java`:
```java
package nz.compliance.engine.rules;

public enum Comparator {
    LTE("≤") { public boolean test(double v, double t) { return v <= t; } },
    GTE("≥") { public boolean test(double v, double t) { return v >= t; } },
    EQ("=")       { public boolean test(double v, double t) { return v == t; } };

    private final String symbol;

    Comparator(String symbol) { this.symbol = symbol; }

    public abstract boolean test(double value, double threshold);

    public String symbol() { return symbol; }
}
```

`engine/src/main/java/nz/compliance/engine/rules/Severity.java`:
```java
package nz.compliance.engine.rules;

public enum Severity { ERROR, WARNING }
```

- [ ] **Step 2: Create `Rule` and `RuleSet`**

`engine/src/main/java/nz/compliance/engine/rules/Rule.java`:
```java
package nz.compliance.engine.rules;

import nz.compliance.engine.model.BuildingContext;

import java.util.Set;

/**
 * One machine-checkable constraint. {@code riskGroups} empty == applies to all.
 * v1 threshold is a single value; context-keyed threshold tables are a later enhancement.
 */
public record Rule(String id, String citation, String title, ParameterKey parameter,
                   Comparator comparator, double threshold, Severity severity, Set<String> riskGroups) {

    public Rule {
        riskGroups = riskGroups == null ? Set.of() : Set.copyOf(riskGroups);
    }

    public boolean appliesTo(BuildingContext ctx) {
        return riskGroups.isEmpty() || (ctx.riskGroup() != null && riskGroups.contains(ctx.riskGroup()));
    }
}
```

`engine/src/main/java/nz/compliance/engine/rules/RuleSet.java`:
```java
package nz.compliance.engine.rules;

import nz.compliance.engine.model.BuildingContext;

import java.util.List;

public record RuleSet(String name, String version, List<Rule> rules) {

    public RuleSet {
        rules = List.copyOf(rules);
    }

    /** Filters to rules applicable to the given context. */
    public List<Rule> resolve(BuildingContext ctx) {
        return rules.stream().filter(r -> r.appliesTo(ctx)).toList();
    }
}
```

- [ ] **Step 3: Write + run a small test for resolution and comparators**

`engine/src/test/java/nz/compliance/engine/rules/RuleSetTest.java`:
```java
package nz.compliance.engine.rules;

import nz.compliance.engine.model.BuildingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSetTest {

    @Test
    void resolve_keepsOnlyApplicableRiskGroups() {
        Rule wbOnly = new Rule("r1", "c", "t", ParameterKey.EXIT_COUNT, Comparator.GTE, 2, Severity.ERROR, Set.of("WB"));
        Rule all = new Rule("r2", "c", "t", ParameterKey.OPEN_PATH_LENGTH, Comparator.LTE, 18, Severity.ERROR, Set.of());
        RuleSet rs = new RuleSet("nz", "v1", List.of(wbOnly, all));

        assertThat(rs.resolve(new BuildingContext("WB", true, 3.0))).extracting(Rule::id).containsExactly("r1", "r2");
        assertThat(rs.resolve(new BuildingContext("CA", true, 3.0))).extracting(Rule::id).containsExactly("r2");
    }

    @Test
    void comparators_behaveAsExpected() {
        assertThat(Comparator.LTE.test(5, 10)).isTrue();
        assertThat(Comparator.GTE.test(1, 2)).isFalse();
    }
}
```

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=RuleSetTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git -C /workspace add engine/src/main/java/nz/compliance/engine/model/BuildingContext.java engine/src/main/java/nz/compliance/engine/rules engine/src/test/java/nz/compliance/engine/rules
git -C /workspace commit -m "feat(engine): data-driven rule model (Rule/RuleSet/ParameterKey/Comparator)"
```

---

## Task 2: ParameterRegistry + ComplianceEngine evaluator (TDD)

**Files:**
- Create: `engine/src/main/java/nz/compliance/engine/check/ParameterRegistry.java`
- Create: `engine/src/main/java/nz/compliance/engine/check/Violation.java`
- Create: `engine/src/main/java/nz/compliance/engine/check/OutcomeStatus.java`
- Create: `engine/src/main/java/nz/compliance/engine/check/RuleOutcome.java`
- Create: `engine/src/main/java/nz/compliance/engine/check/CheckResult.java`
- Create: `engine/src/main/java/nz/compliance/engine/check/ComplianceEngine.java`
- Test: `engine/src/test/java/nz/compliance/engine/check/ComplianceEngineTest.java`

- [ ] **Step 1: Write the failing test**

`engine/src/test/java/nz/compliance/engine/check/ComplianceEngineTest.java`:
```java
package nz.compliance.engine.check;

import nz.compliance.engine.model.*;
import nz.compliance.engine.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceEngineTest {

    private static Space rect(String id, double x0, double y0, double x1, double y1) {
        return new Space(id, id, "WB", List.of(
                new Point(x0, y0), new Point(x1, y0), new Point(x1, y1), new Point(x0, y1)));
    }

    private final ComplianceEngine engine = new ComplianceEngine();
    private final BuildingContext ctx = new BuildingContext("WB", true, 3.0);

    // open path <= 10 m, and >= 2 escape routes
    private final RuleSet rules = new RuleSet("nz", "v1", List.of(
            new Rule("openpath", "C/AS2 open path", "Open path", ParameterKey.OPEN_PATH_LENGTH, Comparator.LTE, 10, Severity.ERROR, Set.of()),
            new Rule("exits", "C/AS2 escape routes", "Escape routes", ParameterKey.EXIT_COUNT, Comparator.GTE, 2, Severity.ERROR, Set.of())));

    @Test
    void flagsOpenPathAndExitCountViolations() {
        // s1(0,0-10,10) with the only exit; s2(10,0-20,10) routes through s1 -> 15 m
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s2", 10, 0, 20, 10)),
                List.of(
                        new Door("d12", "s1", "s2", List.of(new Point(10, 4), new Point(10, 6)), 1000, false),
                        new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));

        CheckResult r = engine.check(doc, ctx, rules);

        assertThat(r.blocked()).isFalse();
        // s2 open path 15 > 10 -> violation; exit count 1 < 2 -> violation
        assertThat(r.violations()).extracting(Violation::ruleId).contains("openpath", "exits");
        Violation openPath = r.violations().stream().filter(v -> v.ruleId().equals("openpath")).findFirst().orElseThrow();
        assertThat(openPath.spaceId()).isEqualTo("s2");
        assertThat(openPath.pathNodeIds()).containsExactly("s2", "s1", "EXTERIOR");
        // s1 open path 5 <= 10 -> passed
        assertThat(r.passed()).anyMatch(o -> o.ruleId().equals("openpath") && "s1".equals(o.spaceId()));
    }

    @Test
    void blocksWhenNoExits() {
        GeometryDoc doc = new GeometryDoc(1, List.of(rect("s1", 0, 0, 10, 10)), List.of());
        CheckResult r = engine.check(doc, ctx, rules);
        assertThat(r.blocked()).isTrue();
        assertThat(r.blockMessage()).contains("exit");
    }

    @Test
    void unreachableSpaceBecomesNoMeansOfEscape() {
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s3", 100, 100, 110, 110)),
                List.of(new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
        CheckResult r = engine.check(doc, ctx, rules);
        assertThat(r.violations()).anyMatch(v -> v.ruleId().equals("structural.no-egress") && "s3".equals(v.spaceId()));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=ComplianceEngineTest`
Expected: FAIL — `check` classes not found.

- [ ] **Step 3: Implement the result types**

`engine/src/main/java/nz/compliance/engine/check/OutcomeStatus.java`:
```java
package nz.compliance.engine.check;

public enum OutcomeStatus { PASS, VIOLATION, NOT_EVALUATED }
```

`engine/src/main/java/nz/compliance/engine/check/Violation.java`:
```java
package nz.compliance.engine.check;

import nz.compliance.engine.rules.ParameterKey;
import nz.compliance.engine.rules.Severity;

import java.util.List;

/** A located rule failure. spaceId/pathNodeIds drive highlighting; may be null/empty for plan-level. */
public record Violation(String ruleId, String citation, Severity severity, String message,
                        ParameterKey parameter, Double computedValue, Double threshold,
                        String spaceId, List<String> pathNodeIds) {
    public Violation {
        pathNodeIds = pathNodeIds == null ? List.of() : List.copyOf(pathNodeIds);
    }
}
```

`engine/src/main/java/nz/compliance/engine/check/RuleOutcome.java`:
```java
package nz.compliance.engine.check;

import nz.compliance.engine.rules.ParameterKey;

public record RuleOutcome(String ruleId, ParameterKey parameter, String spaceId,
                          Double computedValue, OutcomeStatus status, String reason) {
}
```

`engine/src/main/java/nz/compliance/engine/check/CheckResult.java`:
```java
package nz.compliance.engine.check;

import java.util.List;

public record CheckResult(List<Violation> violations, List<RuleOutcome> passed,
                          List<RuleOutcome> notEvaluated, boolean blocked, String blockMessage) {

    public CheckResult {
        violations = List.copyOf(violations);
        passed = List.copyOf(passed);
        notEvaluated = List.copyOf(notEvaluated);
    }

    public static CheckResult blocked(String message) {
        return new CheckResult(List.of(), List.of(), List.of(), true, message);
    }
}
```

- [ ] **Step 4: Implement the parameter registry**

`engine/src/main/java/nz/compliance/engine/check/ParameterRegistry.java`:
```java
package nz.compliance.engine.check;

import nz.compliance.engine.egress.EgressResult;
import nz.compliance.engine.egress.SpaceEgress;
import nz.compliance.engine.facts.PlanFacts;
import nz.compliance.engine.facts.SpaceFacts;
import nz.compliance.engine.rules.ParameterKey;

import java.util.Optional;

/** Bridges rule parameters to computed facts/egress. Add a case here to support a new parameter. */
final class ParameterRegistry {

    private ParameterRegistry() {
    }

    static Optional<Double> value(ParameterKey key, PlanFacts facts, EgressResult egress, String spaceId) {
        return switch (key) {
            case OPEN_PATH_LENGTH -> {
                SpaceEgress se = egress.bySpace().get(spaceId);
                yield se == null ? Optional.empty() : se.openPathLengthMetres();
            }
            case OCCUPANT_LOAD -> facts.space(spaceId).map(SpaceFacts::occupantLoad);
            case EXIT_COUNT -> Optional.of((double) facts.exitDoorCount());
            case EXIT_WIDTH -> Optional.of(facts.totalExitWidthMillimetres());
        };
    }
}
```

- [ ] **Step 5: Implement the engine**

`engine/src/main/java/nz/compliance/engine/check/ComplianceEngine.java`:
```java
package nz.compliance.engine.check;

import nz.compliance.engine.egress.EgressAnalyzer;
import nz.compliance.engine.egress.EgressResult;
import nz.compliance.engine.egress.SpaceEgress;
import nz.compliance.engine.facts.FactsComputer;
import nz.compliance.engine.facts.PlanFacts;
import nz.compliance.engine.model.BuildingContext;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Space;
import nz.compliance.engine.rules.ParameterKey;
import nz.compliance.engine.rules.Rule;
import nz.compliance.engine.rules.RuleSet;
import nz.compliance.engine.rules.Scope;
import nz.compliance.engine.rules.Severity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The deterministic compliance check. Pure: no Spring, DB, web, or LLM. */
public final class ComplianceEngine {

    public CheckResult check(GeometryDoc doc, BuildingContext ctx, RuleSet ruleSet) {
        PlanFacts facts = FactsComputer.compute(doc);
        EgressResult egress = EgressAnalyzer.analyze(doc);

        if (facts.exitDoorCount() == 0) {
            return CheckResult.blocked(
                    "No exits defined — add at least one exit door to check means of escape.");
        }

        Map<String, String> names = new HashMap<>();
        for (Space s : doc.spaces()) {
            names.put(s.id(), s.name());
        }

        List<Violation> violations = new ArrayList<>();
        List<RuleOutcome> passed = new ArrayList<>();
        List<RuleOutcome> notEvaluated = new ArrayList<>();

        for (SpaceEgress se : egress.unreachable()) {
            violations.add(new Violation("structural.no-egress", "C/AS2 means of escape", Severity.ERROR,
                    "No means of escape: " + label(names, se.spaceId()) + " cannot reach any exit.",
                    null, null, null, se.spaceId(), List.of()));
        }

        for (Rule rule : ruleSet.resolve(ctx)) {
            if (rule.parameter().scope() == Scope.PER_SPACE) {
                for (Space s : doc.spaces()) {
                    evaluate(rule, s.id(), facts, egress, names, violations, passed, notEvaluated);
                }
            } else {
                evaluate(rule, null, facts, egress, names, violations, passed, notEvaluated);
            }
        }
        return new CheckResult(violations, passed, notEvaluated, false, null);
    }

    private void evaluate(Rule rule, String spaceId, PlanFacts facts, EgressResult egress,
                          Map<String, String> names, List<Violation> violations,
                          List<RuleOutcome> passed, List<RuleOutcome> notEvaluated) {
        Optional<Double> value = ParameterRegistry.value(rule.parameter(), facts, egress, spaceId);
        if (value.isEmpty()) {
            notEvaluated.add(new RuleOutcome(rule.id(), rule.parameter(), spaceId, null,
                    OutcomeStatus.NOT_EVALUATED, "no value (e.g. unreachable space)"));
            return;
        }
        double v = value.get();
        if (rule.comparator().test(v, rule.threshold())) {
            passed.add(new RuleOutcome(rule.id(), rule.parameter(), spaceId, v, OutcomeStatus.PASS, "ok"));
            return;
        }
        List<String> path = (rule.parameter() == ParameterKey.OPEN_PATH_LENGTH && spaceId != null)
                ? egress.bySpace().get(spaceId).pathNodeIds() : List.of();
        violations.add(new Violation(rule.id(), rule.citation(), rule.severity(),
                message(rule, spaceId, names, v), rule.parameter(), v, rule.threshold(), spaceId, path));
    }

    private String message(Rule rule, String spaceId, Map<String, String> names, double v) {
        String where = spaceId == null ? "Plan" : label(names, spaceId);
        return switch (rule.parameter()) {
            case OPEN_PATH_LENGTH -> "Open path from " + where + " is " + round(v) + " m; max "
                    + round(rule.threshold()) + " m (" + rule.citation() + ").";
            case EXIT_COUNT -> where + " has " + (int) v + " escape route(s); minimum "
                    + (int) rule.threshold() + " (" + rule.citation() + ").";
            case EXIT_WIDTH -> where + " has " + round(v) + " mm exit width; minimum "
                    + round(rule.threshold()) + " mm (" + rule.citation() + ").";
            case OCCUPANT_LOAD -> where + " occupant load " + round(v) + "; limit "
                    + round(rule.threshold()) + " (" + rule.citation() + ").";
        };
    }

    private static String round(double d) {
        return String.valueOf(Math.round(d * 10.0) / 10.0);
    }

    private static String label(Map<String, String> names, String id) {
        String n = names.get(id);
        return (n == null ? id : n) + " (" + id + ")";
    }
}
```

- [ ] **Step 6: Run to verify it passes; then full engine suite**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=ComplianceEngineTest`
Expected: PASS — 3 green.
Run: `cd /workspace && ./mvnw -q -B -pl engine test`
Expected: PASS — whole engine suite green.

- [ ] **Step 7: Commit**

```bash
git -C /workspace add engine/src/main/java/nz/compliance/engine/check engine/src/test/java/nz/compliance/engine/check
git -C /workspace commit -m "feat(engine): ComplianceEngine evaluator with parameter registry and located violations"
```

---

## Task 3: App — JobRunr + CheckRun persistence

**Files:**
- Modify: `app/pom.xml`
- Modify: `app/src/main/resources/application.yml`
- Create: `app/src/main/resources/db/migration/V2__check_runs.sql`
- Create: `app/src/main/java/nz/compliance/app/check/CheckRun.java`
- Create: `app/src/main/java/nz/compliance/app/check/CheckRunRepository.java`

- [ ] **Step 1: Add JobRunr to `app/pom.xml`**

```xml
    <dependency>
      <groupId>org.jobrunr</groupId>
      <artifactId>jobrunr-spring-boot-3-starter</artifactId>
      <version>7.3.1</version>
    </dependency>
```

- [ ] **Step 2: Configure JobRunr** — append to `app/src/main/resources/application.yml`:
```yaml
org:
  jobrunr:
    background-job-server:
      enabled: true
    dashboard:
      enabled: true
    database:
      type: sql
```

- [ ] **Step 3: Migration for check_runs** — `app/src/main/resources/db/migration/V2__check_runs.sql`:
```sql
create table check_runs (
    id            uuid primary key,
    floor_plan_id uuid not null references floor_plans(id) on delete cascade,
    status        text not null,
    result_json   jsonb,
    error         text,
    created_at    timestamptz not null default now(),
    finished_at   timestamptz
);
create index idx_check_runs_floor_plan on check_runs (floor_plan_id);
```
> JobRunr creates its own tables automatically on first start (`jobrunr_*`). No migration needed for those.

- [ ] **Step 4: CheckRun entity + repository**

`app/src/main/java/nz/compliance/app/check/CheckRun.java`:
```java
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
```

`app/src/main/java/nz/compliance/app/check/CheckRunRepository.java`:
```java
package nz.compliance.app.check;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CheckRunRepository extends JpaRepository<CheckRun, UUID> {
}
```

- [ ] **Step 5: Verify compile**

Run: `cd /workspace && ./mvnw -q -B -pl app -am test-compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git -C /workspace add app/pom.xml app/src/main/resources/application.yml app/src/main/resources/db/migration/V2__check_runs.sql app/src/main/java/nz/compliance/app/check
git -C /workspace commit -m "feat(app): JobRunr + CheckRun entity/migration"
```

---

## Task 4: App — default ruleset + check service + JobRunr job + API

**Files:**
- Create: `app/src/main/java/nz/compliance/app/check/DefaultNzEgressRuleSet.java`
- Create: `app/src/main/java/nz/compliance/app/check/CheckService.java`
- Create: `app/src/main/java/nz/compliance/app/check/CheckJob.java`
- Create: `app/src/main/java/nz/compliance/app/check/dto/CheckRunDto.java`
- Create: `app/src/main/java/nz/compliance/app/check/CheckController.java`
- Test: `app/src/test/java/nz/compliance/app/check/CheckFlowIT.java`

- [ ] **Step 1: Hand-authored NZ egress ruleset** (illustrative thresholds; replaced by LLM-codified rules in Plan 6)

`app/src/main/java/nz/compliance/app/check/DefaultNzEgressRuleSet.java`:
```java
package nz.compliance.app.check;

import nz.compliance.engine.rules.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class DefaultNzEgressRuleSet {

    public RuleSet ruleSet() {
        return new RuleSet("NZBC C/AS2 — Means of Escape (illustrative)", "v1", List.of(
                new Rule("openpath.commercial", "C/AS2 open path (illustrative)", "Max open path",
                        ParameterKey.OPEN_PATH_LENGTH, Comparator.LTE, 18.0, Severity.ERROR, Set.of()),
                new Rule("escape-routes.min", "C/AS2 escape routes (illustrative)", "Min escape routes",
                        ParameterKey.EXIT_COUNT, Comparator.GTE, 2.0, Severity.ERROR, Set.of())));
    }
}
```

- [ ] **Step 2: Check service** (creates the run, enqueues the job)

`app/src/main/java/nz/compliance/app/check/CheckService.java`:
```java
package nz.compliance.app.check;

import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import nz.compliance.app.project.FloorPlanRepository;

import java.util.UUID;

@Service
public class CheckService {

    private final CheckRunRepository runs;
    private final FloorPlanRepository floorPlans;
    private final JobScheduler jobScheduler;

    public CheckService(CheckRunRepository runs, FloorPlanRepository floorPlans, JobScheduler jobScheduler) {
        this.runs = runs;
        this.floorPlans = floorPlans;
        this.jobScheduler = jobScheduler;
    }

    public UUID startCheck(UUID floorPlanId) {
        if (!floorPlans.existsById(floorPlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "floor plan not found");
        }
        CheckRun run = runs.save(new CheckRun(floorPlanId));
        UUID runId = run.getId();
        jobScheduler.enqueue(() -> runCheck(runId));
        return runId;
    }

    // placeholder so the lambda above references a concrete method; real work is in CheckJob
    public void runCheck(UUID runId) {
        throw new UnsupportedOperationException("wired in Step 3");
    }
}
```
> We replace `runCheck` with a delegation to `CheckJob` in the next step to keep the JobRunr lambda a simple method reference. (JobRunr serializes the lambda as a method call.)

- [ ] **Step 3: The job + final service wiring** — replace `CheckService.runCheck` to delegate, and add `CheckJob`:

`app/src/main/java/nz/compliance/app/check/CheckJob.java`:
```java
package nz.compliance.app.check;

import nz.compliance.app.project.FloorPlan;
import nz.compliance.app.project.FloorPlanRepository;
import nz.compliance.engine.check.CheckResult;
import nz.compliance.engine.check.ComplianceEngine;
import nz.compliance.engine.model.BuildingContext;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class CheckJob {

    private final CheckRunRepository runs;
    private final FloorPlanRepository floorPlans;
    private final DefaultNzEgressRuleSet ruleSet;
    private final ComplianceEngine engine = new ComplianceEngine();

    public CheckJob(CheckRunRepository runs, FloorPlanRepository floorPlans, DefaultNzEgressRuleSet ruleSet) {
        this.runs = runs;
        this.floorPlans = floorPlans;
        this.ruleSet = ruleSet;
    }

    @Job(name = "compliance-check")
    public void run(UUID runId) {
        CheckRun run = runs.findById(runId).orElseThrow();
        try {
            run.setStatus(CheckRun.Status.RUNNING);
            runs.save(run);

            FloorPlan fp = floorPlans.findById(run.getFloorPlanId()).orElseThrow();
            BuildingContext ctx = new BuildingContext(fp.getRiskGroup(),
                    Boolean.TRUE.equals(fp.getSprinklered()), fp.getEscapeHeightMetres());
            CheckResult result = engine.check(fp.getGeometry(), ctx, ruleSet.ruleSet());

            run.setResult(result);
            run.setStatus(CheckRun.Status.SUCCEEDED);
        } catch (Exception e) {
            run.setStatus(CheckRun.Status.FAILED);
            run.setError(e.getMessage());
        } finally {
            run.setFinishedAt(Instant.now());
            runs.save(run);
        }
    }
}
```

Replace `CheckService` with the version that enqueues `CheckJob.run`:
```java
package nz.compliance.app.check;

import nz.compliance.app.project.FloorPlanRepository;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CheckService {

    private final CheckRunRepository runs;
    private final FloorPlanRepository floorPlans;
    private final JobScheduler jobScheduler;
    private final CheckJob checkJob;

    public CheckService(CheckRunRepository runs, FloorPlanRepository floorPlans,
                        JobScheduler jobScheduler, CheckJob checkJob) {
        this.runs = runs;
        this.floorPlans = floorPlans;
        this.jobScheduler = jobScheduler;
        this.checkJob = checkJob;
    }

    public UUID startCheck(UUID floorPlanId) {
        if (!floorPlans.existsById(floorPlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "floor plan not found");
        }
        UUID runId = runs.save(new CheckRun(floorPlanId)).getId();
        jobScheduler.enqueue(() -> checkJob.run(runId));
        return runId;
    }
}
```

- [ ] **Step 4: Result DTO + controller**

`app/src/main/java/nz/compliance/app/check/dto/CheckRunDto.java`:
```java
package nz.compliance.app.check.dto;

import nz.compliance.app.check.CheckRun;
import nz.compliance.engine.check.CheckResult;

import java.util.UUID;

public record CheckRunDto(UUID id, UUID floorPlanId, String status, CheckResult result, String error) {
    public static CheckRunDto from(CheckRun r) {
        return new CheckRunDto(r.getId(), r.getFloorPlanId(), r.getStatus().name(), r.getResult(), r.getError());
    }
}
```

`app/src/main/java/nz/compliance/app/check/CheckController.java`:
```java
package nz.compliance.app.check;

import nz.compliance.app.check.dto.CheckRunDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CheckController {

    private final CheckService checkService;
    private final CheckRunRepository runs;

    public CheckController(CheckService checkService, CheckRunRepository runs) {
        this.checkService = checkService;
        this.runs = runs;
    }

    @PostMapping("/floorplans/{id}/checks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, UUID> start(@PathVariable UUID id) {
        return Map.of("runId", checkService.startCheck(id));
    }

    @GetMapping("/checks/{runId}")
    public CheckRunDto get(@PathVariable UUID runId) {
        return runs.findById(runId).map(CheckRunDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
```

- [ ] **Step 5: Write the end-to-end integration test** (runs the job inline)

`app/src/test/java/nz/compliance/app/check/CheckFlowIT.java`:
```java
package nz.compliance.app.check;

import nz.compliance.app.project.FloorPlan;
import nz.compliance.app.project.FloorPlanRepository;
import nz.compliance.app.project.Project;
import nz.compliance.app.project.ProjectRepository;
import nz.compliance.app.support.PostgresIntegrationTest;
import nz.compliance.engine.check.CheckResult;
import nz.compliance.engine.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckFlowIT extends PostgresIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired FloorPlanRepository floorPlans;
    @Autowired CheckJob checkJob;
    @Autowired CheckRunRepository runs;

    @Test
    void runsCheckAndStoresViolations() {
        Project p = projects.save(new Project("P"));
        FloorPlan fp = new FloorPlan(p.getId(), "L1");
        fp.setRiskGroup("WB");
        fp.setSprinklered(true);
        // single space with one exit, occupant load fine, but only 1 escape route -> EXIT_COUNT violation
        fp.setGeometry(new GeometryDoc(1,
                List.of(new Space("s1", "Office", "WB",
                        List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)))),
                List.of(new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true))));
        fp = floorPlans.save(fp);

        CheckRun run = runs.save(new CheckRun(fp.getId()));
        checkJob.run(run.getId());   // run synchronously (no scheduler in the test)

        CheckRun reloaded = runs.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CheckRun.Status.SUCCEEDED);
        CheckResult result = reloaded.getResult();
        assertThat(result.violations()).anyMatch(v -> v.ruleId().equals("escape-routes.min"));
    }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=CheckFlowIT`
Expected: PASS (Testcontainers; the job runs inline and persists a `CheckResult` as jsonb).

- [ ] **Step 7: Commit**

```bash
git -C /workspace add app/src/main/java/nz/compliance/app/check app/src/test/java/nz/compliance/app/check/CheckFlowIT.java
git -C /workspace commit -m "feat(app): async compliance check (JobRunr) with default NZ egress ruleset + API"
```

---

## Task 5: Frontend — run a check + render violations

**Files:**
- Create: `frontend/src/api/checks.ts`
- Create: `frontend/src/editor/ResultsPanel.tsx`
- Modify: `frontend/src/editor/EditorCanvas.tsx` (highlight violation spaces + path)
- Modify: `frontend/src/editor/EditorPage.tsx` (Check button + polling)

- [ ] **Step 1: Checks API client + types**

`frontend/src/api/checks.ts`:
```ts
import { api } from './projects'

export interface Violation {
  ruleId: string
  citation: string
  severity: string
  message: string
  spaceId: string | null
  pathNodeIds: string[]
}

export interface CheckResult {
  violations: Violation[]
  passed: unknown[]
  notEvaluated: unknown[]
  blocked: boolean
  blockMessage: string | null
}

export interface CheckRun {
  id: string
  floorPlanId: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  result: CheckResult | null
  error: string | null
}

export const startCheck = (floorPlanId: string) =>
  api.jsonFetch<{ runId: string }>(`/api/floorplans/${floorPlanId}/checks`, { method: 'POST' })

export const getCheck = (runId: string) => api.jsonFetch<CheckRun>(`/api/checks/${runId}`)

export async function pollCheck(runId: string, intervalMs = 600, timeoutMs = 30000): Promise<CheckRun> {
  const start = Date.now()
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const run = await getCheck(runId)
    if (run.status === 'SUCCEEDED' || run.status === 'FAILED') return run
    if (Date.now() - start > timeoutMs) throw new Error('check timed out')
    await new Promise((r) => setTimeout(r, intervalMs))
  }
}
```

- [ ] **Step 2: Results panel**

`frontend/src/editor/ResultsPanel.tsx`:
```tsx
import type { CheckResult } from '../api/checks'

export default function ResultsPanel({ result }: { result: CheckResult | null }) {
  if (!result) return null
  if (result.blocked) return <p role="alert">⚠️ {result.blockMessage}</p>
  if (result.violations.length === 0) return <p>✅ No violations found.</p>
  return (
    <ul>
      {result.violations.map((v, i) => (
        <li key={i} style={{ color: '#c5221f' }}>❌ {v.message}</li>
      ))}
    </ul>
  )
}
```

- [ ] **Step 3: Highlight violations on the canvas** — replace `frontend/src/editor/EditorCanvas.tsx` with a version that accepts highlight props:
```tsx
import type { GeometryDoc, Point } from '../api/types'

const SCALE = 20 // pixels per metre

interface Props {
  doc: GeometryDoc
  draft: Point[]
  onCanvasClick: (worldPoint: Point) => void
  violationSpaceIds?: string[]
  pathNodeIds?: string[]    // space ids along a highlighted egress path
}

export default function EditorCanvas({
  doc, draft, onCanvasClick, violationSpaceIds = [], pathNodeIds = [],
}: Props) {
  function handleClick(e: React.MouseEvent<SVGSVGElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    onCanvasClick({ x: (e.clientX - rect.left) / SCALE, y: (e.clientY - rect.top) / SCALE })
  }
  const poly = (pts: Point[]) => pts.map((p) => `${p.x * SCALE},${p.y * SCALE}`).join(' ')
  const centroid = (pts: Point[]): Point => ({
    x: pts.reduce((a, p) => a + p.x, 0) / pts.length,
    y: pts.reduce((a, p) => a + p.y, 0) / pts.length,
  })
  const byId = Object.fromEntries(doc.spaces.map((s) => [s.id, s]))

  return (
    <svg width={800} height={600} role="img" aria-label="floor plan canvas"
         style={{ border: '1px solid #ccc', background: '#fafafa' }} onClick={handleClick}>
      {doc.spaces.map((s) => (
        <polygon key={s.id} points={poly(s.polygon)}
                 fill={violationSpaceIds.includes(s.id) ? '#fce8e6' : '#e8f0fe'}
                 stroke={violationSpaceIds.includes(s.id) ? '#c5221f' : '#3367d6'} />
      ))}
      {doc.doors.map((d) => (
        <line key={d.id}
              x1={d.position[0].x * SCALE} y1={d.position[0].y * SCALE}
              x2={d.position[1].x * SCALE} y2={d.position[1].y * SCALE}
              stroke={d.exit ? '#0b8043' : '#999'} strokeWidth={4} />
      ))}
      {pathNodeIds.filter((id) => byId[id]).length > 1 && (
        <polyline fill="none" stroke="#c5221f" strokeWidth={2} strokeDasharray="6"
                  points={poly(pathNodeIds.filter((id) => byId[id]).map((id) => centroid(byId[id].polygon)))} />
      )}
      {draft.length > 0 && (
        <polyline points={poly(draft)} fill="none" stroke="#ff6d00" strokeDasharray="4" />
      )}
    </svg>
  )
}
```

- [ ] **Step 4: Wire the Check button + polling into `EditorPage`** — add to the existing `EditorPage.tsx`: import the checks API + ResultsPanel, add state, a `runCheck` handler, and render. Replace the file's body with:
```tsx
import { useEffect, useState } from 'react'
import type { Point } from '../api/types'
import { getFloorPlan, saveFloorPlan } from '../api/floorplans'
import { startCheck, pollCheck, type CheckResult } from '../api/checks'
import { useFloorPlan } from './useFloorPlan'
import EditorCanvas from './EditorCanvas'
import Toolbar, { type Mode } from './Toolbar'
import ResultsPanel from './ResultsPanel'

interface Props { floorPlanId: string }

export default function EditorPage({ floorPlanId }: Props) {
  const fp = useFloorPlan()
  const [mode, setMode] = useState<Mode>('space')
  const [draft, setDraft] = useState<Point[]>([])
  const [name, setName] = useState('Level 1')
  const [saving, setSaving] = useState(false)
  const [checking, setChecking] = useState(false)
  const [result, setResult] = useState<CheckResult | null>(null)

  useEffect(() => {
    getFloorPlan(floorPlanId).then((loaded) => { fp.setDoc(loaded.geometry); setName(loaded.name) })
      .catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [floorPlanId])

  function onCanvasClick(p: Point) {
    if (mode === 'space') setDraft((d) => [...d, p])
    else if (mode === 'exitDoor' && fp.doc.spaces.length > 0) {
      const space = fp.doc.spaces[fp.doc.spaces.length - 1]
      fp.commitDoor(space.id, null, [{ x: p.x, y: p.y - 0.5 }, { x: p.x, y: p.y + 0.5 }], true)
    }
  }
  function finishSpace() { if (draft.length >= 3) fp.commitSpace(draft); setDraft([]) }

  async function save() {
    setSaving(true)
    try {
      await saveFloorPlan(floorPlanId, { name, riskGroup: 'WB', sprinklered: true, escapeHeightMetres: 3, geometry: fp.doc })
    } finally { setSaving(false) }
  }

  async function runCheck() {
    setChecking(true); setResult(null)
    try {
      await save()
      const { runId } = await startCheck(floorPlanId)
      const run = await pollCheck(runId)
      setResult(run.result)
    } finally { setChecking(false) }
  }

  const violationSpaceIds = (result?.violations ?? []).map((v) => v.spaceId).filter((x): x is string => !!x)
  const pathNodeIds = result?.violations.find((v) => v.pathNodeIds.length > 0)?.pathNodeIds ?? []

  return (
    <main>
      <h1>compliance-checker — editor</h1>
      <label>Plan name <input value={name} onChange={(e) => setName(e.target.value)} /></label>
      <Toolbar mode={mode} onMode={setMode} onFinishSpace={finishSpace} onSave={save} saving={saving} />
      <button onClick={runCheck} disabled={checking}>{checking ? 'Checking…' : 'Check compliance'}</button>
      <EditorCanvas doc={fp.doc} draft={draft} onCanvasClick={onCanvasClick}
                    violationSpaceIds={violationSpaceIds} pathNodeIds={pathNodeIds} />
      <ResultsPanel result={result} />
      <p>Spaces: {fp.doc.spaces.length} · Doors: {fp.doc.doors.length}</p>
    </main>
  )
}
```

- [ ] **Step 5: Update the canvas test for the new optional props** — the existing `EditorCanvas.test.tsx` still passes (new props are optional). Run all frontend tests:

Run: `cd /workspace/frontend && npm run test`
Expected: PASS.

- [ ] **Step 6: Manual end-to-end demo**

```bash
cd /workspace && docker compose up -d db
DB_URL=jdbc:postgresql://localhost:5432/compliance ./mvnw -B -pl app spring-boot:run   # shell 1
cd /workspace/frontend && npm run dev                                                  # shell 2
```
In the browser: draw two adjacent spaces, add a single exit door, click **Check compliance**. Expect a violation list (e.g. "… has 1 escape route(s); minimum 2 …") and the offending space outlined red. (JobRunr dashboard at `http://localhost:8000` shows the job.)

- [ ] **Step 7: Commit**

```bash
git -C /workspace add frontend/src/api/checks.ts frontend/src/editor
git -C /workspace commit -m "feat(frontend): run compliance check and render located violations"
```

---

## Definition of done (Plan 5)

- `./mvnw verify` green (engine evaluator tests + app `CheckFlowIT`); `npm run test` green.
- End-to-end: draw → **Check** → violations listed and the offending space(s)/egress path highlighted.
- No exits → blocked message; unreachable space → "no means of escape" violation.

## Self-review notes

- **Spec coverage:** rule model as data + resolution + evaluation (spec §9 steps 0,5) ✓; parameter registry (§9) ✓; located violations + 3-outcome model + "no exits"/"unreachable" handling (§9, §11) ✓; async JobRunr CheckRun + geometry-backed check + viewer rendering (§5, §8) ✓. Threshold *tables by context* are simplified to single thresholds for v1 (noted); LLM-authored rules arrive in Plan 6 (the `DefaultNzEgressRuleSet` is the seam).
- **Placeholders:** none in code. Rule thresholds/citations are flagged illustrative (domain data), not code stubs.
- **Type consistency:** engine `CheckResult/Violation/RuleOutcome` are persisted as jsonb and surfaced unchanged through `CheckRunDto`; the frontend `Violation`/`CheckResult` interfaces mirror them (`spaceId`, `pathNodeIds`, `blocked`, `blockMessage`). `BuildingContext(riskGroup,sprinklered,escapeHeightMetres)` matches the `FloorPlan` fields mapped in `CheckJob`. Endpoints `/api/floorplans/{id}/checks`, `/api/checks/{runId}` match controller ↔ client.
```
