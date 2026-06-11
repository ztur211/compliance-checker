# Architecture

## Modules
- **engine/** — pure Java library (JTS geometry, JGraphT pathfinding). No Spring/DB/web/LLM.
  `ComplianceEngine.check(GeometryDoc, BuildingContext, RuleSet) → CheckResult`.
- **app/** — Spring Boot: REST API, Postgres (JPA + Flyway), JobRunr async checks, LLM rule codification.
- **frontend/** — React + TypeScript SVG editor/viewer + rule-review page.

## Check flow
draw → save geometry (jsonb) → POST check → JobRunr worker runs the pure engine
(resolve rules → facts → egress graph → Dijkstra → evaluate) → store located violations → frontend polls + highlights.

## Rule codification (authoring time)
C/AS2 text → PDFBox → clause chunks → LangChain4j/Claude (schema-constrained) → validate →
human review (approve/edit/reject, with provenance) → versioned active RuleSet → consumed by checks.
The LLM never runs during a check.

## Key decisions
Pure deterministic engine module · rules as data + parameter registry · LLM at authoring time +
mandatory human-in-the-loop · architect-for-many-jurisdictions-populate-one · centroid door-graph path
metric in v1 (navmesh on the roadmap). Full rationale: `docs/superpowers/specs/2026-06-09-compliance-checker-nz-fire-egress-design.md`.
