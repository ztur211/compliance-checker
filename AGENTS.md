# compliance-checker

## What this is
Automated building-code compliance checking for the construction industry. v1 checks New Zealand
commercial fire egress (means of escape, NZBC C/AS2) on a 2D floor plan: you draw or import a plan,
a deterministic engine computes occupant load, builds an egress graph, runs Dijkstra to each space's
nearest exit, and reports located violations. Compliance rules are codified from C/AS2 text by an LLM
at authoring time and human-approved before activation; the check path itself never calls an LLM.

## Build / test / run
Requires JDK 21, Node 20+, and a running Docker daemon (Testcontainers + Postgres).

- build: `./mvnw -B verify` (engine + app; includes Testcontainers integration tests)
- test:  `./mvnw -B verify` and `cd frontend && npm test`
- run:   `docker compose up -d db`
         `./mvnw -pl app -am spring-boot:run`   # backend :8080
         `cd frontend && npm run dev`           # frontend :5173

Slow/networked tests are tagged `eval` and excluded from the default run (`-DexcludedGroups=` to include).
Rule extraction and vision import need `ANTHROPIC_API_KEY`; without it the app falls back to a built-in
illustrative rule set and a stub extractor.

## Conventions
- `engine/` is a pure library: JTS + JGraphT, no Spring, no DB, no web, no LLM. Keep it that way -
  it is what makes checks deterministic, testable, and auditable.
- Rules are data (parameter + comparator + threshold), not code. New rules go through the parameter
  registry, not new `if` branches in the engine.
- The LLM runs only at authoring/import time, never inside a check. A check must be reproducible from
  (geometry, building context, rule set version) alone.
- Comments explain constraints the code cannot show (see the pom comments on Testcontainers/Docker API
  versions and the spring-boot-maven-plugin skip). Do not add narration comments.

## Boundaries
- Do not put a model, an API call, or any nondeterminism in the check path.
- Do not hand-edit anything generated, and do not weaken a test to make it pass.
- This is a design aid, not a legal compliance sign-off; keep the disclaimer in the UI.
