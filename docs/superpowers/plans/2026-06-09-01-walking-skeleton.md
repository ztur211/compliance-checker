# Walking Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a deployed, CI'd, full-stack backbone — a multi-module Java build (`engine` + `app`), a Spring Boot API with a `/api/health` endpoint, a React/TypeScript frontend that displays backend status, a combined Docker image, GitHub Actions CI, and a live Fly.io deploy.

**Architecture:** Maven multi-module reactor. The `engine` module is a pure Java library with **no Spring/web/DB dependency** (the build enforces the boundary); the `app` module is the Spring Boot application and depends on `engine`. The React app is served two ways: in dev by the Vite dev server (proxying `/api` to Spring on `:8080`), and in prod baked into the Spring Boot jar's static resources so there is a single deployable artifact. **No database yet** — Postgres + persistence arrive in Plan 2.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Maven (wrapper), JUnit 5 + AssertJ, React 18 + TypeScript + Vite, Vitest + Testing Library, Docker (multi-stage), GitHub Actions, Fly.io.

---

## File map (created in this plan)

```
/workspace
├── pom.xml                         # Maven parent (reactor)
├── mvnw, mvnw.cmd, .mvn/           # Maven wrapper
├── .gitignore
├── Dockerfile                      # multi-stage: frontend build → backend build → JRE runtime
├── docker-compose.yml              # run the combined prod-like image locally
├── fly.toml                        # Fly.io deploy config
├── README.md
├── .github/workflows/ci.yml        # CI: backend verify + frontend test/build
├── engine/
│   ├── pom.xml
│   └── src/{main,test}/java/nz/compliance/engine/EngineInfo[.java|Test.java]
├── app/
│   ├── pom.xml
│   └── src/
│       ├── main/java/nz/compliance/app/ComplianceCheckerApplication.java
│       ├── main/java/nz/compliance/app/health/{HealthController,HealthResponse}.java
│       ├── main/resources/application.yml
│       └── test/java/nz/compliance/app/health/HealthControllerTest.java
└── frontend/                       # scaffolded with Vite (react-ts), then customized
    ├── package.json, vite.config.ts, tsconfig*.json, index.html
    └── src/{App.tsx, App.test.tsx, setupTests.ts, api/health.ts, main.tsx}
```

---

## Task 1: Maven multi-module skeleton

**Files:**
- Create: `/workspace/pom.xml`
- Create: `/workspace/engine/pom.xml`
- Create: `/workspace/app/pom.xml`
- Create: `/workspace/.gitignore`
- Create (generated): `/workspace/mvnw`, `/workspace/mvnw.cmd`, `/workspace/.mvn/wrapper/maven-wrapper.properties`

- [ ] **Step 1: Create the parent POM**

`/workspace/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>nz.compliance</groupId>
  <artifactId>compliance-checker-parent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-boot.version>3.3.5</spring-boot.version>
  </properties>

  <modules>
    <module>engine</module>
    <module>app</module>
  </modules>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>3.13.0</version>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>3.5.1</version>
        </plugin>
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <version>${spring-boot.version}</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

- [ ] **Step 2: Create the `engine` module POM** (pure Java, no Spring)

`/workspace/engine/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>nz.compliance</groupId>
    <artifactId>compliance-checker-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>engine</artifactId>

  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: Create the `app` module POM** (Spring Boot, depends on engine)

`/workspace/app/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>nz.compliance</groupId>
    <artifactId>compliance-checker-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>app</artifactId>

  <dependencies>
    <dependency>
      <groupId>nz.compliance</groupId>
      <artifactId>engine</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <executions>
          <execution>
            <goals>
              <goal>repackage</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: Create `.gitignore`**

`/workspace/.gitignore`:
```
# Java / Maven
target/
*.class

# Node
node_modules/
frontend/dist/

# Env / secrets
.env
.env.local

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
```

- [ ] **Step 5: Generate the Maven wrapper**

Run: `cd /workspace && mvn -N wrapper:wrapper -Dmaven=3.9.9`
Expected: creates `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`; `BUILD SUCCESS`.

- [ ] **Step 6: Verify the reactor builds (empty modules)**

Run: `cd /workspace && ./mvnw -q -B compile`
Expected: `BUILD SUCCESS` building both `engine` and `app` (no sources yet is fine).

- [ ] **Step 7: Commit**

```bash
git -C /workspace add pom.xml engine/pom.xml app/pom.xml .gitignore mvnw mvnw.cmd .mvn
git -C /workspace commit -m "build: scaffold Maven multi-module skeleton (engine + app)"
```

---

## Task 2: `engine` module — EngineInfo (TDD)

**Files:**
- Test: `/workspace/engine/src/test/java/nz/compliance/engine/EngineInfoTest.java`
- Create: `/workspace/engine/src/main/java/nz/compliance/engine/EngineInfo.java`

- [ ] **Step 1: Write the failing test**

`/workspace/engine/src/test/java/nz/compliance/engine/EngineInfoTest.java`:
```java
package nz.compliance.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineInfoTest {

    @Test
    void describe_returnsNameAndVersion() {
        assertThat(EngineInfo.describe()).isEqualTo("compliance-engine 0.1.0");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl engine test`
Expected: FAIL — compilation error, `cannot find symbol: EngineInfo`.

- [ ] **Step 3: Write the minimal implementation**

`/workspace/engine/src/main/java/nz/compliance/engine/EngineInfo.java`:
```java
package nz.compliance.engine;

/** Build/identity info for the pure compliance engine module. */
public final class EngineInfo {

    public static final String NAME = "compliance-engine";
    public static final String VERSION = "0.1.0";

    private EngineInfo() {
    }

    public static String describe() {
        return NAME + " " + VERSION;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl engine test`
Expected: PASS — `Tests run: 1, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git -C /workspace add engine/src
git -C /workspace commit -m "feat(engine): add EngineInfo with version describe()"
```

---

## Task 3: `app` module — Spring Boot + /api/health (TDD)

**Files:**
- Create: `/workspace/app/src/main/java/nz/compliance/app/ComplianceCheckerApplication.java`
- Create: `/workspace/app/src/main/resources/application.yml`
- Test: `/workspace/app/src/test/java/nz/compliance/app/health/HealthControllerTest.java`
- Create: `/workspace/app/src/main/java/nz/compliance/app/health/HealthController.java`
- Create: `/workspace/app/src/main/java/nz/compliance/app/health/HealthResponse.java`

- [ ] **Step 1: Create the Spring Boot application class and config**

`/workspace/app/src/main/java/nz/compliance/app/ComplianceCheckerApplication.java`:
```java
package nz.compliance.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ComplianceCheckerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceCheckerApplication.class, args);
    }
}
```

`/workspace/app/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: compliance-checker
server:
  port: 8080
```

- [ ] **Step 2: Write the failing test** (web-layer slice; asserts status + engine version)

`/workspace/app/src/test/java/nz/compliance/app/health/HealthControllerTest.java`:
```java
package nz.compliance.app.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void health_returnsOkAndEngineVersion() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.engine").value("compliance-engine 0.1.0"));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl app -am test`
Expected: FAIL — compilation error, `HealthController` / `HealthResponse` not found.

- [ ] **Step 4: Write the minimal implementation**

`/workspace/app/src/main/java/nz/compliance/app/health/HealthResponse.java`:
```java
package nz.compliance.app.health;

public record HealthResponse(String status, String engine) {
}
```

`/workspace/app/src/main/java/nz/compliance/app/health/HealthController.java`:
```java
package nz.compliance.app.health;

import nz.compliance.engine.EngineInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", EngineInfo.describe());
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl app -am test`
Expected: PASS — `Tests run: 1, Failures: 0`. (This also proves the `app → engine` dependency: the response includes `EngineInfo.describe()`.)

- [ ] **Step 6: Manually verify the running app**

Run: `cd /workspace && ./mvnw -B -pl app spring-boot:run` (leave running), then in another shell: `curl -s localhost:8080/api/health`
Expected: `{"status":"ok","engine":"compliance-engine 0.1.0"}`. Stop with Ctrl-C.

- [ ] **Step 7: Commit**

```bash
git -C /workspace add app/src
git -C /workspace commit -m "feat(app): Spring Boot app with /api/health reporting engine version"
```

---

## Task 4: Frontend — Vite React/TS health display (TDD)

**Files:**
- Scaffold: `/workspace/frontend/` (Vite `react-ts` template)
- Create: `/workspace/frontend/vite.config.ts`
- Create: `/workspace/frontend/src/setupTests.ts`
- Create: `/workspace/frontend/src/api/health.ts`
- Test: `/workspace/frontend/src/App.test.tsx`
- Modify: `/workspace/frontend/src/App.tsx`
- Modify: `/workspace/frontend/package.json` (add `test` script)

- [ ] **Step 1: Scaffold the frontend and add test deps**

Run:
```bash
cd /workspace && npm create vite@latest frontend -- --template react-ts
cd /workspace/frontend && npm install
npm install -D vitest@^2 jsdom @testing-library/react@^16 @testing-library/jest-dom@^6
```
Expected: `frontend/` created with a standard Vite React-TS project; deps installed; `frontend/package-lock.json` exists.

- [ ] **Step 2: Add the `test` script to package.json**

In `/workspace/frontend/package.json`, ensure the `scripts` block contains:
```json
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run"
  },
```

- [ ] **Step 3: Configure Vite (dev proxy + Vitest)**

Replace `/workspace/frontend/vite.config.ts` with:
```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/setupTests.ts',
  },
})
```

Create `/workspace/frontend/src/setupTests.ts`:
```ts
import '@testing-library/jest-dom'
```

- [ ] **Step 4: Write the failing test**

`/workspace/frontend/src/App.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import App from './App'

describe('App', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('shows backend status from /api/health', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ status: 'ok', engine: 'compliance-engine 0.1.0' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    render(<App />)

    expect(await screen.findByText(/Backend status:/)).toBeInTheDocument()
    expect(screen.getByText('ok')).toBeInTheDocument()
    expect(screen.getByText(/compliance-engine 0\.1\.0/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `cd /workspace/frontend && npm run test`
Expected: FAIL — current `App.tsx` (Vite default counter) renders no "Backend status:" text.

- [ ] **Step 6: Write the implementation**

Create `/workspace/frontend/src/api/health.ts`:
```ts
export interface Health {
  status: string
  engine: string
}

export async function fetchHealth(): Promise<Health> {
  const res = await fetch('/api/health')
  if (!res.ok) {
    throw new Error(`health check failed: ${res.status}`)
  }
  return (await res.json()) as Health
}
```

Replace `/workspace/frontend/src/App.tsx` with:
```tsx
import { useEffect, useState } from 'react'
import { fetchHealth, type Health } from './api/health'

export default function App() {
  const [health, setHealth] = useState<Health | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchHealth()
      .then(setHealth)
      .catch((e) => setError(String(e)))
  }, [])

  return (
    <main>
      <h1>compliance-checker</h1>
      {error && <p role="alert">Backend unreachable: {error}</p>}
      {health ? (
        <p>
          Backend status: <strong>{health.status}</strong> ({health.engine})
        </p>
      ) : (
        !error && <p>Checking backend…</p>
      )}
    </main>
  )
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd /workspace/frontend && npm run test`
Expected: PASS — 1 passed.

- [ ] **Step 8: Verify the production build succeeds**

Run: `cd /workspace/frontend && npm run build`
Expected: `dist/` produced, no TypeScript errors.

- [ ] **Step 9: Commit**

```bash
git -C /workspace add frontend
git -C /workspace commit -m "feat(frontend): React/TS app showing backend health"
```

---

## Task 5: Combined Docker image + local compose

**Files:**
- Create: `/workspace/Dockerfile`
- Create: `/workspace/docker-compose.yml`

- [ ] **Step 1: Create the multi-stage Dockerfile** (frontend build → backend build with frontend baked into static → JRE runtime)

`/workspace/Dockerfile`:
```dockerfile
# --- Stage 1: build the frontend ---
FROM node:20-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the backend jar (frontend dist baked into static) ---
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /src
COPY pom.xml ./
COPY engine/pom.xml engine/pom.xml
COPY app/pom.xml app/pom.xml
COPY engine engine
COPY app app
COPY --from=frontend /fe/dist app/src/main/resources/static
RUN mvn -q -B -pl app -am package -DskipTests

# --- Stage 3: runtime ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /run
COPY --from=backend /src/app/target/app-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Create docker-compose for a local prod-like run**

`/workspace/docker-compose.yml`:
```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
```

- [ ] **Step 3: Build and run the combined image**

Run: `cd /workspace && docker compose up --build -d`
Expected: image builds through all three stages; container `app` starts.

- [ ] **Step 4: Verify API and frontend are served from one origin**

Run: `curl -s localhost:8080/api/health` → Expected: `{"status":"ok","engine":"compliance-engine 0.1.0"}`
Run: `curl -s localhost:8080/ | grep -o '<title>[^<]*</title>'` → Expected: the frontend's HTML title (e.g. `<title>Vite + React + TS</title>` or your customized title).
Then: `cd /workspace && docker compose down`

- [ ] **Step 5: Commit**

```bash
git -C /workspace add Dockerfile docker-compose.yml
git -C /workspace commit -m "build: combined multi-stage Docker image (frontend + backend)"
```

---

## Task 6: GitHub Actions CI

**Files:**
- Create: `/workspace/.github/workflows/ci.yml`

- [ ] **Step 1: Create the CI workflow** (backend `verify`, frontend test + build)

`/workspace/.github/workflows/ci.yml`:
```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Build and test
        run: ./mvnw -B verify

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
      - run: npm run test
      - run: npm run build
```

- [ ] **Step 2: Verify the backend job command locally** (the exact command CI runs)

Run: `cd /workspace && ./mvnw -B verify`
Expected: `BUILD SUCCESS`; both modules' tests run and pass.

- [ ] **Step 3: Verify the frontend job commands locally**

Run: `cd /workspace/frontend && npm ci && npm run test && npm run build`
Expected: deps install, test passes, build produces `dist/`.

- [ ] **Step 4: Commit**

```bash
git -C /workspace add .github/workflows/ci.yml
git -C /workspace commit -m "ci: add GitHub Actions backend + frontend pipeline"
```

> After pushing to a GitHub remote, confirm the **CI** workflow runs green on the Actions tab. (Creating the remote is part of Task 8.)

---

## Task 7: Deploy to Fly.io

> This task requires **interactive login** to Fly.io and a Fly account. Run the `fly` commands yourself in this session by prefixing with `!` (e.g. `! fly auth login`), since they open a browser / need your credentials.

**Files:**
- Create: `/workspace/fly.toml`

- [ ] **Step 1: Create the Fly config**

`/workspace/fly.toml` (change `app` to a globally-unique name):
```toml
app = "compliance-checker-CHANGEME"
primary_region = "syd"

[build]
  dockerfile = "Dockerfile"

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 0

[[vm]]
  size = "shared-cpu-1x"
  memory = "1gb"
```

- [ ] **Step 2: Install flyctl and log in** (interactive — run yourself)

```bash
! curl -L https://fly.io/install.sh | sh        # if flyctl not already installed
! fly auth login
```
Expected: browser login completes; `fly auth whoami` shows your account.

- [ ] **Step 3: Pick a unique app name and create the app**

Edit `fly.toml` `app = "..."` to a unique value, then:
```bash
! fly apps create <your-unique-name>
```
Expected: `New app created: <your-unique-name>`.

- [ ] **Step 4: Deploy**

```bash
! fly deploy
```
Expected: image builds remotely (or locally), machine starts, deploy reports success with an app URL like `https://<your-unique-name>.fly.dev`.

- [ ] **Step 5: Verify the live deploy**

```bash
! curl -s https://<your-unique-name>.fly.dev/api/health
```
Expected: `{"status":"ok","engine":"compliance-engine 0.1.0"}`. Opening the root URL in a browser shows the frontend with "Backend status: ok".

> If the machine OOMs on boot, bump `memory` in `fly.toml` to `2gb` and re-run `fly deploy`.

- [ ] **Step 6: Commit**

```bash
git -C /workspace add fly.toml
git -C /workspace commit -m "deploy: add Fly.io config for combined image"
```

---

## Task 8: README + GitHub remote + final tag

**Files:**
- Create: `/workspace/README.md`

- [ ] **Step 1: Write the README**

`/workspace/README.md`:
```markdown
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
```
./mvnw -pl app spring-boot:run
```
Frontend (port 5173, proxies `/api` to 8080):
```
cd frontend && npm install && npm run dev
```

## Test
```
./mvnw verify          # backend (engine + app)
cd frontend && npm run test
```

## Run the production image locally
```
docker compose up --build
# open http://localhost:8080
```

## Deploy
```
fly deploy             # see fly.toml
```
```

- [ ] **Step 2: Create a GitHub remote and push** (interactive auth — run yourself)

```bash
! gh repo create compliance-checker --private --source /workspace --remote origin --push
```
Expected: repo created, `origin` set, `main` pushed. (Alternatively create the repo in the GitHub UI and `git -C /workspace remote add origin <url> && git -C /workspace push -u origin main`.)

- [ ] **Step 3: Confirm CI is green**

On the GitHub repo's **Actions** tab, confirm the **CI** workflow for the pushed `main` is green (both `backend` and `frontend` jobs).

- [ ] **Step 4: Commit the README and tag the milestone**

```bash
git -C /workspace add README.md
git -C /workspace commit -m "docs: add README"
git -C /workspace tag v0.1.0-skeleton
git -C /workspace push origin main --tags
```

---

## Definition of done (Plan 1)

- `./mvnw verify` passes (engine + app tests green); `cd frontend && npm run test` passes.
- `docker compose up --build` serves the frontend at `/` and `/api/health` from one origin.
- GitHub Actions **CI** is green on `main`.
- A live Fly.io URL serves the app; `GET /api/health` returns `{"status":"ok","engine":"compliance-engine 0.1.0"}`.
- Repo tagged `v0.1.0-skeleton`.

## Self-review notes

- **Spec coverage (Plan 1 slice):** multi-module build with pure `engine` (spec §4, §9 boundary) ✓; Spring Boot API (§5) ✓; React/TS frontend (§5) ✓; Docker + CI + live deploy (§3 maturity, §13) ✓. Postgres/persistence (§6) intentionally deferred to Plan 2; engine domain logic (§9) to Plans 3–4; AI (§10) to Plan 6 — all out of this slice by design.
- **Placeholders:** none — every file has complete content. The only intentional fill-ins are user-specific deploy values (`app = "...CHANGEME"`, `<your-unique-name>`), which cannot be known ahead of time.
- **Type/name consistency:** `nz.compliance` groupId, `0.1.0-SNAPSHOT` version, jar `app-0.1.0-SNAPSHOT.jar`, `EngineInfo.describe()` → `"compliance-engine 0.1.0"`, and the `{status, engine}` health contract are consistent across the POMs, Java, Dockerfile, frontend `Health` interface, and tests.
```
