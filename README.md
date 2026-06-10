# compliance-checker

A building-code compliance checker. **v1** checks **New Zealand commercial fire egress**
(means of escape, per NZBC C/AS2) on a 2D floor plan, using a pure rules engine
(computational geometry + graph shortest-path) and an LLM pipeline that codifies code
text into reviewable rules.

> Design-aid / pre-check only — **not** a legal compliance sign-off.

See the design spec in `docs/superpowers/specs/` and plans in `docs/superpowers/plans/`.

## Stack
Java 21 · Spring Boot · (engine: JTS + JGraphT) · React + TypeScript (Vite) · Docker · Fly.io

## Develop
Backend (port 8080):

    ./mvnw -pl app spring-boot:run

Frontend (port 5173, proxies `/api` to 8080):

    cd frontend && npm install && npm run dev

## Test

    ./mvnw verify          # backend (engine + app)
    cd frontend && npm run test

## Run the production image locally

    docker compose up --build
    # open http://localhost:8080

## Deploy

    fly deploy             # see fly.toml
