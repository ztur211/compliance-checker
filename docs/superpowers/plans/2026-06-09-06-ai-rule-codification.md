# AI Rule-Codification Pipeline Implementation Plan (Plan 6)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-authored ruleset with an offline LLM pipeline: ingest C/AS2 text, extract schema-constrained `RuleCandidate`s (LangChain4j → Claude), validate them deterministically, queue them for **human review** with provenance, and activate approved rules into a **versioned, DB-stored RuleSet** the check engine consumes. Plus a precision/recall **eval** harness.

**Architecture:** The LLM runs only at authoring time, behind a `RuleExtractor` interface (real `LangChain4jRuleExtractor`; tests use a stub — no live API in CI). Extracted candidates are validated against the engine's `ParameterKey`/`Comparator` vocabulary, stored as `DRAFT` rules with `sourceQuote`/`confidence`, reviewed (approve/edit/reject), and promoted to `ACTIVE`. `RuleSetService.activeRuleSet()` builds the engine `RuleSet` from active rows (falling back to `DefaultNzEgressRuleSet` when none exist). Key tables: `rule_sets`, `rules`, `extraction_runs`. **Tables/numbers stay human-certified; hand-entered threshold tables remain authoritative for the actual values (spec §11).**

**Tech Stack:** LangChain4j + Anthropic (Claude), Apache PDFBox, Spring Boot/JPA; React review page.

**Prerequisite:** Plan 5 complete (engine `Rule`/`RuleSet`, `DefaultNzEgressRuleSet`, `CheckJob`).

**Secrets:** the real extractor needs `ANTHROPIC_API_KEY` in the environment. It is never required for the app to boot or for tests (which stub it).

---

## File map

```
app/pom.xml                                                  # + langchain4j, + pdfbox
app/src/main/resources/db/migration/V3__rules.sql
app/src/main/java/nz/compliance/app/rules/
  RuleCandidate.java  ExtractionOutput.java
  RuleExtractor.java  LangChain4jRuleExtractor.java  AiRuleExtractor.java
  CandidateValidator.java
  ClauseChunker.java  PdfClauseReader.java  Clause.java
  RuleSetEntity.java  RuleEntity.java  RuleSetRepository.java  RuleRepository.java
  ExtractionService.java  RuleReviewService.java  RuleSetService.java
  RuleAdminController.java  dto/RuleDto.java
app/src/test/java/nz/compliance/app/rules/
  CandidateValidatorTest.java  ClauseChunkerTest.java  ExtractionServiceIT.java  RuleSetServiceIT.java
  GoldSetEvalTest.java        # @Tag("eval"), manual
app/src/main/java/nz/compliance/app/check/CheckJob.java      # use active RuleSet

frontend/src/api/rules.ts
frontend/src/rules/ReviewPage.tsx
```

---

## Task 1: LangChain4j extractor + candidate model

**Files:**
- Modify: `app/pom.xml`
- Create: `app/src/main/java/nz/compliance/app/rules/{RuleCandidate,ExtractionOutput,RuleExtractor,AiRuleExtractor,LangChain4jRuleExtractor}.java`

- [ ] **Step 1: Add dependencies to `app/pom.xml`**

```xml
    <dependency>
      <groupId>dev.langchain4j</groupId>
      <artifactId>langchain4j</artifactId>
      <version>1.0.1</version>
    </dependency>
    <dependency>
      <groupId>dev.langchain4j</groupId>
      <artifactId>langchain4j-anthropic</artifactId>
      <version>1.0.1</version>
    </dependency>
    <dependency>
      <groupId>org.apache.pdfbox</groupId>
      <artifactId>pdfbox</artifactId>
      <version>3.0.3</version>
    </dependency>
```
> If `1.0.1` is unavailable, use the latest `dev.langchain4j` release and adjust the `AnthropicChatModel` builder names accordingly.

- [ ] **Step 2: The candidate model + extractor interface**

`app/src/main/java/nz/compliance/app/rules/RuleCandidate.java`:
```java
package nz.compliance.app.rules;

import java.util.Set;

/** An LLM-proposed rule, with provenance. parameter/comparator are strings until validated. */
public record RuleCandidate(String citation, String title, String parameter, String comparator,
                            double threshold, Set<String> riskGroups, String sourceQuote, double confidence) {
    public RuleCandidate {
        riskGroups = riskGroups == null ? Set.of() : Set.copyOf(riskGroups);
    }
}
```

`app/src/main/java/nz/compliance/app/rules/ExtractionOutput.java`:
```java
package nz.compliance.app.rules;

import java.util.List;

/** Wrapper the LLM populates (LangChain4j maps the JSON to this POJO). */
public record ExtractionOutput(List<RuleCandidate> rules) {
    public ExtractionOutput {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
```

`app/src/main/java/nz/compliance/app/rules/RuleExtractor.java`:
```java
package nz.compliance.app.rules;

import java.util.List;

/** Seam over the LLM so tests can stub it (no live API in CI). */
public interface RuleExtractor {
    List<RuleCandidate> extract(String clauseText);
}
```

- [ ] **Step 3: The LangChain4j AiService + adapter**

`app/src/main/java/nz/compliance/app/rules/AiRuleExtractor.java`:
```java
package nz.compliance.app.rules;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

interface AiRuleExtractor {

    @SystemMessage("""
        You convert New Zealand building-code (NZBC C/AS2) means-of-escape provisions into
        structured compliance rules. Use ONLY these parameters:
          OPEN_PATH_LENGTH (metres), DEAD_END_LENGTH (metres), OCCUPANT_LOAD,
          EXIT_COUNT, EXIT_WIDTH (millimetres).
        Use ONLY these comparators: LTE, GTE, EQ.
        If a provision does not map cleanly to one parameter, omit it.
        For every rule include the verbatim sourceQuote it came from, a citation, and a
        confidence in [0,1]. Do NOT invent numeric thresholds you cannot see in the text.""")
    ExtractionOutput extract(@UserMessage String clauseText);
}
```

`app/src/main/java/nz/compliance/app/rules/LangChain4jRuleExtractor.java`:
```java
package nz.compliance.app.rules;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!test")   // tests provide a stub instead
public class LangChain4jRuleExtractor implements RuleExtractor {

    private final AiRuleExtractor ai;

    public LangChain4jRuleExtractor(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        ChatModel model = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName("claude-sonnet-4-6")
                .build();
        this.ai = AiServices.create(AiRuleExtractor.class, model);
    }

    @Override
    public List<RuleCandidate> extract(String clauseText) {
        return ai.extract(clauseText).rules();
    }
}
```

- [ ] **Step 4: Verify compile**

Run: `cd /workspace && ./mvnw -q -B -pl app -am test-compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git -C /workspace add app/pom.xml app/src/main/java/nz/compliance/app/rules
git -C /workspace commit -m "feat(app): LangChain4j rule extractor + candidate model"
```

---

## Task 2: Candidate validation + clause chunking (TDD)

**Files:**
- Test: `app/src/test/java/nz/compliance/app/rules/CandidateValidatorTest.java`
- Create: `app/src/main/java/nz/compliance/app/rules/CandidateValidator.java`
- Test: `app/src/test/java/nz/compliance/app/rules/ClauseChunkerTest.java`
- Create: `app/src/main/java/nz/compliance/app/rules/{Clause,ClauseChunker,PdfClauseReader}.java`

- [ ] **Step 1: Validator test**

`app/src/test/java/nz/compliance/app/rules/CandidateValidatorTest.java`:
```java
package nz.compliance.app.rules;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateValidatorTest {

    private final CandidateValidator validator = new CandidateValidator();

    @Test
    void acceptsAWellFormedCandidate() {
        RuleCandidate c = new RuleCandidate("C/AS2 3.x", "Open path",
                "OPEN_PATH_LENGTH", "LTE", 18.0, Set.of("WB"), "open paths shall not exceed...", 0.9);
        assertThat(validator.validate(c)).isEmpty();
    }

    @Test
    void rejectsUnknownParameter() {
        RuleCandidate c = new RuleCandidate("x", "t", "SPRINKLER_PRESSURE", "LTE", 1, Set.of(), "q", 0.5);
        assertThat(validator.validate(c)).get().asString().contains("parameter");
    }

    @Test
    void rejectsUnknownComparatorAndBadThreshold() {
        RuleCandidate c = new RuleCandidate("x", "t", "EXIT_COUNT", "BETWEEN", Double.NaN, Set.of(), "q", 0.5);
        assertThat(validator.validate(c)).isPresent();
    }
}
```

- [ ] **Step 2: Implement the validator**

`app/src/main/java/nz/compliance/app/rules/CandidateValidator.java`:
```java
package nz.compliance.app.rules;

import nz.compliance.engine.rules.Comparator;
import nz.compliance.engine.rules.ParameterKey;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/** Deterministic guardrail around the LLM output. Returns an error message, or empty if valid. */
@Component
public class CandidateValidator {

    public Optional<String> validate(RuleCandidate c) {
        if (c.parameter() == null || Arrays.stream(ParameterKey.values()).noneMatch(p -> p.name().equals(c.parameter()))) {
            return Optional.of("unknown parameter: " + c.parameter());
        }
        if (c.comparator() == null || Arrays.stream(Comparator.values()).noneMatch(k -> k.name().equals(c.comparator()))) {
            return Optional.of("unknown comparator: " + c.comparator());
        }
        if (Double.isNaN(c.threshold()) || Double.isInfinite(c.threshold())) {
            return Optional.of("threshold is not a finite number");
        }
        if (c.sourceQuote() == null || c.sourceQuote().isBlank()) {
            return Optional.of("missing sourceQuote (no provenance)");
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 3: Run validator test**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=CandidateValidatorTest`
Expected: PASS.

- [ ] **Step 4: Clause chunker test**

`app/src/test/java/nz/compliance/app/rules/ClauseChunkerTest.java`:
```java
package nz.compliance.app.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClauseChunkerTest {

    @Test
    void splitsOnClauseHeadings() {
        String text = """
            3.1 Open paths
            Open paths shall not exceed the lengths in Table 3.

            3.2 Dead ends
            Dead-end open paths shall not exceed 6 m.
            """;
        var clauses = new ClauseChunker().chunk(text);
        assertThat(clauses).hasSize(2);
        assertThat(clauses.get(0).citation()).isEqualTo("3.1");
        assertThat(clauses.get(1).text()).contains("Dead-end");
    }
}
```

- [ ] **Step 5: Implement chunker + clause + PDF reader**

`app/src/main/java/nz/compliance/app/rules/Clause.java`:
```java
package nz.compliance.app.rules;

public record Clause(String citation, String text) {
}
```

`app/src/main/java/nz/compliance/app/rules/ClauseChunker.java`:
```java
package nz.compliance.app.rules;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits document text into clauses on numbered headings like "3.1", "3.1.2". */
@Component
public class ClauseChunker {

    private static final Pattern HEADING = Pattern.compile("(?m)^\\s*(\\d+(?:\\.\\d+)+)\\s+");

    public List<Clause> chunk(String text) {
        Matcher m = HEADING.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> cites = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            cites.add(m.group(1));
        }
        List<Clause> clauses = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            clauses.add(new Clause(cites.get(i), text.substring(starts.get(i), end).trim()));
        }
        return clauses;
    }
}
```

`app/src/main/java/nz/compliance/app/rules/PdfClauseReader.java`:
```java
package nz.compliance.app.rules;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class PdfClauseReader {

    private final ClauseChunker chunker;

    public PdfClauseReader(ClauseChunker chunker) {
        this.chunker = chunker;
    }

    public List<Clause> read(InputStream pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.readAllBytes())) {
            String text = new PDFTextStripper().getText(doc);
            return chunker.chunk(text);
        }
    }
}
```

- [ ] **Step 6: Run chunker test; commit**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=ClauseChunkerTest`
Expected: PASS.
```bash
git -C /workspace add app/src/main/java/nz/compliance/app/rules app/src/test/java/nz/compliance/app/rules
git -C /workspace commit -m "feat(app): candidate validator + PDF clause chunker"
```

---

## Task 3: Rule persistence (migration + entities)

**Files:**
- Create: `app/src/main/resources/db/migration/V3__rules.sql`
- Create: `app/src/main/java/nz/compliance/app/rules/{RuleSetEntity,RuleEntity,RuleSetRepository,RuleRepository}.java`

- [ ] **Step 1: Migration** — `app/src/main/resources/db/migration/V3__rules.sql`:
```sql
create table rule_sets (
    id          uuid primary key,
    name        text not null,
    version     text not null,
    active      boolean not null default false,
    created_at  timestamptz not null default now()
);

create table rules (
    id            uuid primary key,
    rule_set_id   uuid not null references rule_sets(id) on delete cascade,
    citation      text not null,
    title         text,
    parameter     text not null,
    comparator    text not null,
    threshold     double precision not null,
    severity      text not null default 'ERROR',
    risk_groups   text,                 -- comma-separated; empty = all
    status        text not null default 'DRAFT',  -- DRAFT | APPROVED | REJECTED
    source_quote  text,
    confidence    double precision,
    created_at    timestamptz not null default now()
);

create index idx_rules_set on rules (rule_set_id);
create index idx_rules_status on rules (status);

create table extraction_runs (
    id            uuid primary key,
    rule_set_id   uuid references rule_sets(id) on delete set null,
    source        text,
    model         text,
    candidate_count int not null default 0,
    created_at    timestamptz not null default now()
);
```

- [ ] **Step 2: Entities + repositories** (compact JPA; `RuleStatus` enum stored as string)

`app/src/main/java/nz/compliance/app/rules/RuleSetEntity.java`:
```java
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
```

`app/src/main/java/nz/compliance/app/rules/RuleEntity.java`:
```java
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
```

`app/src/main/java/nz/compliance/app/rules/RuleSetRepository.java`:
```java
package nz.compliance.app.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RuleSetRepository extends JpaRepository<RuleSetEntity, UUID> {
    Optional<RuleSetEntity> findFirstByActiveTrueOrderByCreatedAtDesc();
}
```

`app/src/main/java/nz/compliance/app/rules/RuleRepository.java`:
```java
package nz.compliance.app.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {
    List<RuleEntity> findByRuleSetId(UUID ruleSetId);
    List<RuleEntity> findByRuleSetIdAndStatus(UUID ruleSetId, RuleEntity.RuleStatus status);
    List<RuleEntity> findByStatus(RuleEntity.RuleStatus status);
}
```

- [ ] **Step 3: Compile + commit**

Run: `cd /workspace && ./mvnw -q -B -pl app -am test-compile`
Expected: `BUILD SUCCESS`.
```bash
git -C /workspace add app/src/main/resources/db/migration/V3__rules.sql app/src/main/java/nz/compliance/app/rules
git -C /workspace commit -m "feat(app): rule_sets/rules persistence (versioned, provenance, status)"
```

---

## Task 4: Extraction + review + active-ruleset services (TDD with a stub)

**Files:**
- Create: `app/src/main/java/nz/compliance/app/rules/ExtractionService.java`
- Create: `app/src/main/java/nz/compliance/app/rules/RuleReviewService.java`
- Create: `app/src/main/java/nz/compliance/app/rules/RuleSetService.java`
- Test: `app/src/test/java/nz/compliance/app/rules/ExtractionServiceIT.java`
- Test: `app/src/test/java/nz/compliance/app/rules/RuleSetServiceIT.java`

- [ ] **Step 1: Extraction service** (read clauses → extract → validate → store DRAFT)

`app/src/main/java/nz/compliance/app/rules/ExtractionService.java`:
```java
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
```

- [ ] **Step 2: Review service** (approve/edit/reject; build the engine RuleSet from APPROVED rows; activate)

`app/src/main/java/nz/compliance/app/rules/RuleReviewService.java`:
```java
package nz.compliance.app.rules;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RuleReviewService {

    private final RuleRepository rules;
    private final RuleSetRepository ruleSets;

    public RuleReviewService(RuleRepository rules, RuleSetRepository ruleSets) {
        this.rules = rules;
        this.ruleSets = ruleSets;
    }

    public void approve(UUID ruleId) { setStatus(ruleId, RuleEntity.RuleStatus.APPROVED); }
    public void reject(UUID ruleId) { setStatus(ruleId, RuleEntity.RuleStatus.REJECTED); }

    public void edit(UUID ruleId, String parameter, String comparator, double threshold) {
        RuleEntity r = rules.findById(ruleId).orElseThrow();
        r.setParameter(parameter);
        r.setComparator(comparator);
        r.setThreshold(threshold);
        rules.save(r);
    }

    /** Marks a rule set active (deactivating others). Only approved rules will be used. */
    public void activate(UUID ruleSetId) {
        ruleSets.findAll().forEach(rs -> { rs.setActive(rs.getId().equals(ruleSetId)); ruleSets.save(rs); });
    }

    private void setStatus(UUID ruleId, RuleEntity.RuleStatus status) {
        RuleEntity r = rules.findById(ruleId).orElseThrow();
        r.setStatus(status);
        rules.save(r);
    }
}
```

- [ ] **Step 3: Active-ruleset service** (engine `RuleSet` from APPROVED rows; fallback to default)

`app/src/main/java/nz/compliance/app/rules/RuleSetService.java`:
```java
package nz.compliance.app.rules;

import nz.compliance.app.check.DefaultNzEgressRuleSet;
import nz.compliance.engine.rules.*;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class RuleSetService {

    private final RuleSetRepository ruleSets;
    private final RuleRepository rules;
    private final DefaultNzEgressRuleSet fallback;

    public RuleSetService(RuleSetRepository ruleSets, RuleRepository rules, DefaultNzEgressRuleSet fallback) {
        this.ruleSets = ruleSets;
        this.rules = rules;
        this.fallback = fallback;
    }

    public RuleSet activeRuleSet() {
        return ruleSets.findFirstByActiveTrueOrderByCreatedAtDesc()
                .map(this::toEngineRuleSet)
                .filter(rs -> !rs.rules().isEmpty())
                .orElseGet(fallback::ruleSet);
    }

    private RuleSet toEngineRuleSet(RuleSetEntity rs) {
        List<Rule> engineRules = rules.findByRuleSetIdAndStatus(rs.getId(), RuleEntity.RuleStatus.APPROVED).stream()
                .map(this::toEngineRule)
                .toList();
        return new RuleSet(rs.getName(), rs.getVersion(), engineRules);
    }

    private Rule toEngineRule(RuleEntity e) {
        Set<String> groups = e.getRiskGroups() == null || e.getRiskGroups().isBlank()
                ? Set.of() : Set.copyOf(Arrays.asList(e.getRiskGroups().split(",")));
        return new Rule(e.getId().toString(), e.getCitation(), e.getTitle(),
                ParameterKey.valueOf(e.getParameter()), Comparator.valueOf(e.getComparator()),
                e.getThreshold(), Severity.valueOf(e.getSeverity()), groups);
    }
}
```

- [ ] **Step 4: Tests** — a stub extractor (test profile) + the extraction→approve→activeRuleSet flow

`app/src/test/java/nz/compliance/app/rules/ExtractionServiceIT.java`:
```java
package nz.compliance.app.rules;

import nz.compliance.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Import(ExtractionServiceIT.StubConfig.class)
class ExtractionServiceIT extends PostgresIntegrationTest {

    @TestConfiguration
    static class StubConfig {
        @Bean RuleExtractor ruleExtractor() {
            return clause -> List.of(
                    new RuleCandidate("C/AS2 3.1", "Open path", "OPEN_PATH_LENGTH", "LTE", 18.0,
                            Set.of("WB"), "open paths shall not exceed 18 m", 0.92),
                    new RuleCandidate("C/AS2 bad", "junk", "NOPE", "LTE", 1.0, Set.of(), "x", 0.1)); // invalid -> dropped
        }
    }

    @Autowired ExtractionService extraction;
    @Autowired RuleRepository rules;

    @Test
    void storesOnlyValidCandidatesAsDraft() {
        UUID ruleSetId = extraction.extractInto("NZBC C/AS2", "v1", List.of("3.1 Open paths ..."));
        assertThat(rules.findByRuleSetIdAndStatus(ruleSetId, RuleEntity.RuleStatus.DRAFT))
                .hasSize(1)
                .allMatch(r -> r.getParameter().equals("OPEN_PATH_LENGTH"));
    }
}
```

`app/src/test/java/nz/compliance/app/rules/RuleSetServiceIT.java`:
```java
package nz.compliance.app.rules;

import nz.compliance.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSetServiceIT extends PostgresIntegrationTest {

    @Autowired RuleSetRepository ruleSets;
    @Autowired RuleRepository rules;
    @Autowired RuleReviewService review;
    @Autowired RuleSetService service;

    @Test
    void activeRuleSetContainsOnlyApprovedRules() {
        RuleSetEntity rs = ruleSets.save(new RuleSetEntity("NZBC C/AS2", "v1"));
        RuleEntity approved = rules.save(new RuleEntity(rs.getId(),
                new RuleCandidate("c", "t", "EXIT_COUNT", "GTE", 2, Set.of(), "q", 0.9)));
        rules.save(new RuleEntity(rs.getId(),
                new RuleCandidate("c", "t", "OPEN_PATH_LENGTH", "LTE", 18, Set.of(), "q", 0.9))); // stays DRAFT

        review.approve(approved.getId());
        review.activate(rs.getId());

        assertThat(service.activeRuleSet().rules()).extracting(r -> r.parameter().name())
                .containsExactly("EXIT_COUNT");
    }

    @Test
    void fallsBackToDefaultWhenNoActiveApprovedRules() {
        assertThat(service.activeRuleSet().rules()).isNotEmpty(); // DefaultNzEgressRuleSet
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=ExtractionServiceIT,RuleSetServiceIT`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git -C /workspace add app/src/main/java/nz/compliance/app/rules app/src/test/java/nz/compliance/app/rules
git -C /workspace commit -m "feat(app): extraction + review + active-ruleset services"
```

---

## Task 5: Admin API + use the active ruleset in checks

**Files:**
- Create: `app/src/main/java/nz/compliance/app/rules/dto/RuleDto.java`
- Create: `app/src/main/java/nz/compliance/app/rules/RuleAdminController.java`
- Modify: `app/src/main/java/nz/compliance/app/check/CheckJob.java`

- [ ] **Step 1: DTO + admin controller**

`app/src/main/java/nz/compliance/app/rules/dto/RuleDto.java`:
```java
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
```

`app/src/main/java/nz/compliance/app/rules/RuleAdminController.java`:
```java
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
```

- [ ] **Step 2: Make `CheckJob` use the active ruleset** — change its constructor + `run` to inject `RuleSetService` instead of `DefaultNzEgressRuleSet`:

In `app/src/main/java/nz/compliance/app/check/CheckJob.java`, replace the `DefaultNzEgressRuleSet ruleSet` field/param with `RuleSetService ruleSets` and update the engine call:
```java
    // field
    private final nz.compliance.app.rules.RuleSetService ruleSets;
    // constructor param: nz.compliance.app.rules.RuleSetService ruleSets  (assign it)
    // in run(): use ruleSets.activeRuleSet()
            CheckResult result = engine.check(fp.getGeometry(), ctx, ruleSets.activeRuleSet());
```
(Full constructor becomes `public CheckJob(CheckRunRepository runs, FloorPlanRepository floorPlans, nz.compliance.app.rules.RuleSetService ruleSets)`.)

- [ ] **Step 3: Verify `CheckFlowIT` still passes** (now via active ruleset → falls back to default → same EXIT_COUNT violation)

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=CheckFlowIT`
Expected: PASS (fallback default ruleset yields the EXIT_COUNT violation as before).

- [ ] **Step 4: Commit**

```bash
git -C /workspace add app/src/main/java/nz/compliance/app/rules app/src/main/java/nz/compliance/app/check/CheckJob.java
git -C /workspace commit -m "feat(app): rule-review admin API; checks use the active DB ruleset (fallback default)"
```

---

## Task 6: Frontend review page

**Files:**
- Create: `frontend/src/api/rules.ts`
- Create: `frontend/src/rules/ReviewPage.tsx`
- Modify: `frontend/src/App.tsx` (a simple tab toggle: Editor / Review)

- [ ] **Step 1: Rules API client**

`frontend/src/api/rules.ts`:
```ts
import { api } from './projects'

export interface RuleDraft {
  id: string
  citation: string
  parameter: string
  comparator: string
  threshold: number
  status: string
  sourceQuote: string | null
  confidence: number | null
}

export const listDrafts = () => api.jsonFetch<RuleDraft[]>('/api/admin/rules')
export const approve = (id: string) => api.jsonFetch<void>(`/api/admin/rules/${id}/approve`, { method: 'POST' })
export const reject = (id: string) => api.jsonFetch<void>(`/api/admin/rules/${id}/reject`, { method: 'POST' })
```

- [ ] **Step 2: Review page** (shows each candidate beside its source quote with approve/reject)

`frontend/src/rules/ReviewPage.tsx`:
```tsx
import { useEffect, useState } from 'react'
import { listDrafts, approve, reject, type RuleDraft } from '../api/rules'

export default function ReviewPage() {
  const [drafts, setDrafts] = useState<RuleDraft[]>([])
  const refresh = () => listDrafts().then(setDrafts).catch(() => setDrafts([]))
  useEffect(() => { refresh() }, [])

  async function act(id: string, fn: (id: string) => Promise<void>) {
    await fn(id)
    await refresh()
  }

  return (
    <main>
      <h1>Rule review</h1>
      {drafts.length === 0 && <p>No draft rules to review.</p>}
      {drafts.map((d) => (
        <div key={d.id} style={{ border: '1px solid #ddd', padding: 8, marginBottom: 8 }}>
          <strong>{d.parameter} {d.comparator} {d.threshold}</strong> — <em>{d.citation}</em>
          {d.confidence != null && <span> · confidence {d.confidence.toFixed(2)}</span>}
          <blockquote style={{ color: '#555' }}>“{d.sourceQuote}”</blockquote>
          <button onClick={() => act(d.id, approve)}>Approve</button>{' '}
          <button onClick={() => act(d.id, reject)}>Reject</button>
        </div>
      ))}
    </main>
  )
}
```

- [ ] **Step 3: Add an Editor/Review toggle to `App.tsx`** — replace `frontend/src/App.tsx`:
```tsx
import { useEffect, useState } from 'react'
import { createProject } from './api/projects'
import { createFloorPlan } from './api/floorplans'
import EditorPage from './editor/EditorPage'
import ReviewPage from './rules/ReviewPage'

export default function App() {
  const [floorPlanId, setFloorPlanId] = useState<string | null>(null)
  const [tab, setTab] = useState<'editor' | 'review'>('editor')

  useEffect(() => {
    createProject('Demo project')
      .then((p) => createFloorPlan(p.id, 'Level 1'))
      .then((fp) => setFloorPlanId(fp.id))
      .catch((e) => console.error(e))
  }, [])

  return (
    <>
      <nav style={{ display: 'flex', gap: 8, padding: 8 }}>
        <button aria-pressed={tab === 'editor'} onClick={() => setTab('editor')}>Editor</button>
        <button aria-pressed={tab === 'review'} onClick={() => setTab('review')}>Rule review</button>
      </nav>
      {tab === 'review' ? <ReviewPage /> : floorPlanId ? <EditorPage floorPlanId={floorPlanId} /> : <p>Starting…</p>}
    </>
  )
}
```

- [ ] **Step 4: Frontend tests still pass**

Run: `cd /workspace/frontend && npm run test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git -C /workspace add frontend/src/api/rules.ts frontend/src/rules frontend/src/App.tsx
git -C /workspace commit -m "feat(frontend): rule-review page (approve/reject candidates with provenance)"
```

---

## Task 7: Gold-set eval harness (manual)

**Files:**
- Create: `app/src/test/java/nz/compliance/app/rules/GoldSetEvalTest.java`

- [ ] **Step 1: Write the eval** (tagged `eval`; excluded from normal `mvn test`; needs `ANTHROPIC_API_KEY`)

`app/src/test/java/nz/compliance/app/rules/GoldSetEvalTest.java`:
```java
package nz.compliance.app.rules;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Precision/recall of the LLM extractor against ~10 hand-codified provisions.
 * Run manually: ./mvnw -pl app test -Dtest=GoldSetEvalTest -Dgroups=eval
 * Requires ANTHROPIC_API_KEY. This is a METRIC, not a pass/fail CI gate.
 */
@Tag("eval")
class GoldSetEvalTest {

    record Gold(String clause, String expectedParameter, double expectedThreshold) {}

    // Replace clause text + expected values with authoritative C/AS2 provisions during domain validation.
    private static final List<Gold> GOLD = List.of(
            new Gold("3.1 Open paths shall not exceed 18 m.", "OPEN_PATH_LENGTH", 18.0),
            new Gold("Dead-end open paths shall not exceed 6 m.", "DEAD_END_LENGTH", 6.0)
            // ... extend to ~10
    );

    @Test
    void reportPrecisionRecall() {
        RuleExtractor extractor = new LangChain4jRuleExtractor(System.getenv("ANTHROPIC_API_KEY"));
        CandidateValidator validator = new CandidateValidator();

        int truePositives = 0, predicted = 0;
        for (Gold g : GOLD) {
            List<RuleCandidate> got = extractor.extract(g.clause()).stream()
                    .filter(c -> validator.validate(c).isEmpty()).toList();
            predicted += got.size();
            boolean hit = got.stream().anyMatch(c -> c.parameter().equals(g.expectedParameter())
                    && Math.abs(c.threshold() - g.expectedThreshold()) < 0.01);
            if (hit) truePositives++;
        }
        double recall = (double) truePositives / GOLD.size();
        double precision = predicted == 0 ? 0 : (double) truePositives / predicted;
        System.out.printf("Gold-set eval: precision=%.2f recall=%.2f (tp=%d predicted=%d gold=%d)%n",
                precision, recall, truePositives, predicted, GOLD.size());
        assertThat(recall).isGreaterThanOrEqualTo(0.0); // metric only; never fails CI
    }
}
```

- [ ] **Step 2: Configure Surefire to exclude `eval` by default** — in `app/pom.xml`, add under `<build><plugins>` (the plugin version is managed by the parent):
```xml
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <excludedGroups>eval</excludedGroups>
        </configuration>
      </plugin>
```

- [ ] **Step 3: Verify normal test run excludes the eval**

Run: `cd /workspace && ./mvnw -q -B -pl app test`
Expected: PASS, and `GoldSetEvalTest` is **not** executed (no API call in CI).

- [ ] **Step 4: Commit**

```bash
git -C /workspace add app/src/test/java/nz/compliance/app/rules/GoldSetEvalTest.java app/pom.xml
git -C /workspace commit -m "test(app): gold-set extraction eval (manual, excluded from CI)"
```

---

## Definition of done (Plan 6)

- `./mvnw verify` green; the eval test is excluded from the normal run.
- With `ANTHROPIC_API_KEY` set, an extraction run turns C/AS2 clause text into DRAFT candidates (valid ones stored, invalid dropped); the review page approves/rejects them; the active rule set drives checks; absent any active approved rules, checks fall back to `DefaultNzEgressRuleSet`.
- Running `GoldSetEvalTest` with `-Dgroups=eval` prints precision/recall.

## Self-review notes

- **Spec coverage:** LLM extraction constrained to the engine vocabulary (spec §10 step 2) ✓; deterministic validation guardrail (§10 step 3) ✓; human review queue with provenance + approve/edit/reject (§10 step 4) ✓; versioned activation + fallback (§10 step 5) ✓; gold-set eval as a metric not a gate (§10) ✓; LLM stays at authoring time — checks read DB rules, never call the LLM (§10 key decision) ✓; hand-entered tables remain authoritative (§11) — extraction proposes, humans certify.
- **Placeholders:** none in code. Gold-set clauses + threshold *values* are flagged to be replaced with authoritative C/AS2 text (domain data, not a code stub). The LangChain4j version may need a bump (noted).
- **Type consistency:** `RuleCandidate` strings are validated against engine `ParameterKey`/`Comparator` enums before persistence; `RuleSetService.toEngineRule` maps DB rows back to engine `Rule`/`RuleSet` (Plan 5) consumed unchanged by `ComplianceEngine`. Admin endpoints `/api/admin/rules…` match controller ↔ `rules.ts` client.
```
