# compliance-checker

Automated **building-code compliance** checking. **v1** checks **New Zealand commercial fire egress**
(means of escape, NZBC C/AS2) on a 2D floor plan.

> Design-aid / pre-check against the C/AS2 Acceptable Solution — **not** a legal compliance sign-off.

## What it does
- Draw a floor plan (spaces, doors, exits) in the browser.
- A pure, deterministic **rules engine** computes occupant load, builds an **egress graph**, and runs
  **Dijkstra** to find each space's open-path travel distance to the nearest exit.
- Violations are **located** on the plan (offending space + egress path highlighted) and explained.
- Compliance rules are **codified from C/AS2 text by an LLM**, then **human-reviewed** before activation;
  checks run deterministically against the approved, versioned rule set.

## Architecture
Pure `engine` module (JTS + JGraphT, no Spring) ← `app` (Spring Boot, Postgres, JobRunr) ← React/TS frontend.
LLM (LangChain4j + Claude) runs only at **authoring time**, never in the check path. See `docs/ARCHITECTURE.md`
and the design spec in `docs/superpowers/specs/`.

## Run locally
```
docker compose up -d db
./mvnw -pl app -am spring-boot:run             # backend :8080
cd frontend && npm install && npm run dev      # frontend :5173
```

## Test
```
./mvnw verify                                  # engine + app (Testcontainers)
cd frontend && npm run test
```

## Rule extraction (optional)
Set `ANTHROPIC_API_KEY`, then trigger extraction and approve candidates on the **Rule review** tab.
Without an active rule set, checks fall back to a built-in illustrative rule set.

## Deploy
Combined image (`Dockerfile`) to Fly.io with a managed Postgres — see `docs/superpowers/plans/2026-06-09-07-polish-docs-demo.md`.

## Status / roadmap
v1 = NZ commercial fire egress. Roadmap: accessibility (NZS 4121), more risk groups, multi-storey,
IFC import, structural (NZS 3604), other jurisdictions. See the spec.
