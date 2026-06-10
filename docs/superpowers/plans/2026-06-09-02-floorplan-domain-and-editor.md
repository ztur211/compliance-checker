# Floor-Plan Domain + Editor Implementation Plan (Plan 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Postgres-backed persistence and a 2D floor-plan editor: model `Project` and `FloorPlan` (with a typed `geometry` JSON document and NZ building context), expose CRUD APIs, and let the user draw spaces, doors, and exits and save/reload them.

**Architecture:** The canonical geometry types (`Point`, `Space`, `Door`, `GeometryDoc`) live in the pure `engine` module's `model` package (the engine will consume them in Plans 3–4); the `app` module persists a `GeometryDoc` as a Postgres `jsonb` column via Hibernate's JSON mapping, and serves it over REST. The React editor edits a `GeometryDoc` on an SVG canvas and round-trips it to the API. **Units are metric (NZ):** coordinates and distances in **metres**, door clear widths in **millimetres**.

**Tech Stack:** Spring Data JPA + Hibernate 6, Flyway, Postgres 16, Testcontainers; React + TypeScript SVG editor.

**Prerequisite:** Plan 1 complete (multi-module build, app runs, frontend dev server proxies `/api`).

---

## File map (created/modified in this plan)

```
engine/src/main/java/nz/compliance/engine/model/
  Point.java  Space.java  Door.java  GeometryDoc.java
engine/src/test/java/nz/compliance/engine/model/GeometryDocTest.java

app/pom.xml                                                   # +jpa, +postgres, +flyway, +testcontainers
app/src/main/resources/application.yml                       # datasource + jpa + flyway
app/src/main/resources/db/migration/V1__projects_floorplans.sql
app/src/main/java/nz/compliance/app/project/
  Project.java  FloorPlan.java                                # @Entity
  ProjectRepository.java  FloorPlanRepository.java
  ProjectController.java  FloorPlanController.java
  dto/ProjectDto.java  dto/CreateProjectRequest.java
  dto/FloorPlanDto.java  dto/SaveFloorPlanRequest.java
app/src/test/java/nz/compliance/app/support/PostgresIntegrationTest.java
app/src/test/java/nz/compliance/app/project/
  FloorPlanRepositoryIT.java  ProjectControllerIT.java  FloorPlanControllerIT.java

docker-compose.yml                                           # +postgres service

frontend/src/
  api/types.ts  api/projects.ts  api/floorplans.ts
  editor/geometry.ts  editor/useFloorPlan.ts
  editor/EditorCanvas.tsx  editor/Toolbar.tsx  editor/EditorPage.tsx
  editor/useFloorPlan.test.ts  editor/EditorCanvas.test.tsx
```

---

## Task 1: Postgres + JPA + Flyway wiring

**Files:**
- Modify: `app/pom.xml`
- Modify: `app/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Create: `app/src/test/java/nz/compliance/app/support/PostgresIntegrationTest.java`

- [ ] **Step 1: Add dependencies to `app/pom.xml`** (inside `<dependencies>`, after the existing entries)

```xml
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-testcontainers</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
```

- [ ] **Step 2: Configure datasource/JPA/Flyway** — replace `app/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: compliance-checker
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/compliance}
    username: ${DB_USER:compliance}
    password: ${DB_PASSWORD:compliance}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate.format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
server:
  port: 8080
```

- [ ] **Step 3: Add a Postgres service to `docker-compose.yml`** — replace the file:

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: compliance
      POSTGRES_USER: compliance
      POSTGRES_PASSWORD: compliance
    ports:
      - "5432:5432"
    volumes:
      - dbdata:/var/lib/postgresql/data

  app:
    build: .
    environment:
      DB_URL: jdbc:postgresql://db:5432/compliance
      DB_USER: compliance
      DB_PASSWORD: compliance
    depends_on:
      - db
    ports:
      - "8080:8080"

volumes:
  dbdata:
```

- [ ] **Step 4: Create the Testcontainers base class** for integration tests

`app/src/test/java/nz/compliance/app/support/PostgresIntegrationTest.java`:
```java
package nz.compliance.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class PostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

- [ ] **Step 5: Verify the app still compiles** (no migration yet, so don't boot it)

Run: `cd /workspace && ./mvnw -q -B -pl app -am test-compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git -C /workspace add app/pom.xml app/src/main/resources/application.yml docker-compose.yml app/src/test/java/nz/compliance/app/support/PostgresIntegrationTest.java
git -C /workspace commit -m "build(app): add JPA, Flyway, Postgres, Testcontainers wiring"
```

---

## Task 2: Flyway migration — projects & floor_plans

**Files:**
- Create: `app/src/main/resources/db/migration/V1__projects_floorplans.sql`

- [ ] **Step 1: Write the migration**

`app/src/main/resources/db/migration/V1__projects_floorplans.sql`:
```sql
create table projects (
    id          uuid primary key,
    name        text not null,
    created_at  timestamptz not null default now()
);

create table floor_plans (
    id                  uuid primary key,
    project_id          uuid not null references projects(id) on delete cascade,
    name                text not null,
    level               int  not null default 0,
    risk_group          text,
    sprinklered         boolean,
    escape_height_metres double precision,
    geometry_json       jsonb not null default '{"schemaVersion":1,"spaces":[],"doors":[]}'::jsonb,
    schema_version      int  not null default 1,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index idx_floor_plans_project on floor_plans (project_id);
```

- [ ] **Step 2: Start the local DB**

Run: `cd /workspace && docker compose up -d db`
Expected: `db` container healthy on `:5432`.

- [ ] **Step 3: Verify Flyway applies the migration on boot**

Run: `cd /workspace && DB_URL=jdbc:postgresql://localhost:5432/compliance ./mvnw -B -pl app spring-boot:run` (leave running briefly)
Expected logs: Flyway "Successfully applied 1 migration ... V1__projects_floorplans". Stop with Ctrl-C.

- [ ] **Step 4: Commit**

```bash
git -C /workspace add app/src/main/resources/db/migration/V1__projects_floorplans.sql
git -C /workspace commit -m "feat(app): V1 migration for projects and floor_plans"
```

---

## Task 3: Geometry model in the engine (TDD)

**Files:**
- Create: `engine/src/main/java/nz/compliance/engine/model/Point.java`
- Create: `engine/src/main/java/nz/compliance/engine/model/Space.java`
- Create: `engine/src/main/java/nz/compliance/engine/model/Door.java`
- Create: `engine/src/main/java/nz/compliance/engine/model/GeometryDoc.java`
- Test: `engine/src/test/java/nz/compliance/engine/model/GeometryDocTest.java`

- [ ] **Step 1: Write the failing test** (validation rules: spaces need ≥3 points; door must reference an existing space; ids unique)

`engine/src/test/java/nz/compliance/engine/model/GeometryDocTest.java`:
```java
package nz.compliance.engine.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryDocTest {

    private static Space square(String id) {
        return new Space(id, id, "WB", List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)));
    }

    @Test
    void validate_passesForWellFormedDoc() {
        var doc = new GeometryDoc(1,
                List.of(square("s1")),
                List.of(new Door("d1", "s1", null,
                        List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));

        assertThat(doc.validationErrors()).isEmpty();
    }

    @Test
    void validate_rejectsSpaceWithFewerThanThreePoints() {
        var doc = new GeometryDoc(1,
                List.of(new Space("s1", "s1", "WB", List.of(new Point(0, 0), new Point(1, 1)))),
                List.of());

        assertThat(doc.validationErrors()).anyMatch(e -> e.contains("s1") && e.contains("at least 3"));
    }

    @Test
    void validate_rejectsDoorReferencingUnknownSpace() {
        var doc = new GeometryDoc(1,
                List.of(square("s1")),
                List.of(new Door("d1", "ghost", null,
                        List.of(new Point(0, 4), new Point(0, 6)), 1200, false)));

        assertThat(doc.validationErrors()).anyMatch(e -> e.contains("d1") && e.contains("ghost"));
    }

    @Test
    void constructor_isNullSafeForLists() {
        var doc = new GeometryDoc(1, null, null);
        assertThat(doc.spaces()).isEmpty();
        assertThat(doc.doors()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl engine test`
Expected: FAIL — model classes do not exist.

- [ ] **Step 3: Write the model types**

`engine/src/main/java/nz/compliance/engine/model/Point.java`:
```java
package nz.compliance.engine.model;

/** A 2D coordinate in metres. */
public record Point(double x, double y) {
}
```

`engine/src/main/java/nz/compliance/engine/model/Space.java`:
```java
package nz.compliance.engine.model;

import java.util.List;

/**
 * A room/space as a simple polygon (metres). {@code occupancyType} keys the
 * occupant-density lookup (e.g. "WB" working/business, "CA" crowd activity).
 */
public record Space(String id, String name, String occupancyType, List<Point> polygon) {
    public Space {
        polygon = polygon == null ? List.of() : List.copyOf(polygon);
    }
}
```

`engine/src/main/java/nz/compliance/engine/model/Door.java`:
```java
package nz.compliance.engine.model;

import java.util.List;

/**
 * An opening/door on a space boundary. {@code toSpaceId} == null means it
 * connects to the exterior. {@code position} is the 2-point door segment.
 * {@code clearWidthMillimetres} is the clear opening width. {@code exit} marks
 * a final exit / discharge to a safe place.
 */
public record Door(String id, String fromSpaceId, String toSpaceId,
                   List<Point> position, double clearWidthMillimetres, boolean exit) {
    public Door {
        position = position == null ? List.of() : List.copyOf(position);
    }
}
```

`engine/src/main/java/nz/compliance/engine/model/GeometryDoc.java`:
```java
package nz.compliance.engine.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The editable floor-plan geometry document persisted as jsonb. */
public record GeometryDoc(int schemaVersion, List<Space> spaces, List<Door> doors) {

    public GeometryDoc {
        spaces = spaces == null ? List.of() : List.copyOf(spaces);
        doors = doors == null ? List.of() : List.copyOf(doors);
    }

    /** Returns human-readable validation errors; empty means structurally valid. */
    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        Set<String> spaceIds = new HashSet<>();
        for (Space s : spaces) {
            if (!spaceIds.add(s.id())) {
                errors.add("duplicate space id: " + s.id());
            }
            if (s.polygon().size() < 3) {
                errors.add("space " + s.id() + " must have at least 3 points");
            }
        }
        Set<String> doorIds = new HashSet<>();
        for (Door d : doors) {
            if (!doorIds.add(d.id())) {
                errors.add("duplicate door id: " + d.id());
            }
            if (!spaceIds.contains(d.fromSpaceId())) {
                errors.add("door " + d.id() + " references unknown fromSpaceId " + d.fromSpaceId());
            }
            if (d.toSpaceId() != null && !spaceIds.contains(d.toSpaceId())) {
                errors.add("door " + d.id() + " references unknown toSpaceId " + d.toSpaceId());
            }
            if (d.position().size() != 2) {
                errors.add("door " + d.id() + " must have exactly 2 position points");
            }
        }
        return errors;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl engine test`
Expected: PASS — 4 tests green.

- [ ] **Step 5: Commit**

```bash
git -C /workspace add engine/src/main/java/nz/compliance/engine/model engine/src/test/java/nz/compliance/engine/model
git -C /workspace commit -m "feat(engine): geometry model (Point/Space/Door/GeometryDoc) with validation"
```

---

## Task 4: JPA entities + repositories + persistence IT

**Files:**
- Create: `app/src/main/java/nz/compliance/app/project/Project.java`
- Create: `app/src/main/java/nz/compliance/app/project/FloorPlan.java`
- Create: `app/src/main/java/nz/compliance/app/project/ProjectRepository.java`
- Create: `app/src/main/java/nz/compliance/app/project/FloorPlanRepository.java`
- Test: `app/src/test/java/nz/compliance/app/project/FloorPlanRepositoryIT.java`

- [ ] **Step 1: Write the failing integration test** (round-trips a `GeometryDoc` through jsonb)

`app/src/test/java/nz/compliance/app/project/FloorPlanRepositoryIT.java`:
```java
package nz.compliance.app.project;

import nz.compliance.app.support.PostgresIntegrationTest;
import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FloorPlanRepositoryIT extends PostgresIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired FloorPlanRepository floorPlans;

    @Test
    void savesAndReloadsGeometryJson() {
        Project project = projects.save(new Project("Tower A"));

        GeometryDoc geometry = new GeometryDoc(1,
                List.of(new Space("s1", "Office", "WB",
                        List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)))),
                List.of(new Door("d1", "s1", null,
                        List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));

        FloorPlan fp = new FloorPlan(project.getId(), "Level 1");
        fp.setGeometry(geometry);
        fp.setRiskGroup("WB");
        fp.setSprinklered(true);
        UUID id = floorPlans.save(fp).getId();

        FloorPlan reloaded = floorPlans.findById(id).orElseThrow();
        assertThat(reloaded.getGeometry().spaces()).hasSize(1);
        assertThat(reloaded.getGeometry().doors().get(0).exit()).isTrue();
        assertThat(reloaded.getRiskGroup()).isEqualTo("WB");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=FloorPlanRepositoryIT`
Expected: FAIL — entities/repositories do not exist.

- [ ] **Step 3: Write the entities and repositories**

`app/src/main/java/nz/compliance/app/project/Project.java`:
```java
package nz.compliance.app.project;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Project() {
    }

    public Project(String name) {
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`app/src/main/java/nz/compliance/app/project/FloorPlan.java`:
```java
package nz.compliance.app.project;

import jakarta.persistence.*;
import nz.compliance.engine.model.GeometryDoc;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "floor_plans")
public class FloorPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int level = 0;

    @Column(name = "risk_group")
    private String riskGroup;

    private Boolean sprinklered;

    @Column(name = "escape_height_metres")
    private Double escapeHeightMetres;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geometry_json", nullable = false, columnDefinition = "jsonb")
    private GeometryDoc geometry = new GeometryDoc(1, List.of(), List.of());

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected FloorPlan() {
    }

    public FloorPlan(UUID projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getRiskGroup() { return riskGroup; }
    public void setRiskGroup(String riskGroup) { this.riskGroup = riskGroup; }
    public Boolean getSprinklered() { return sprinklered; }
    public void setSprinklered(Boolean sprinklered) { this.sprinklered = sprinklered; }
    public Double getEscapeHeightMetres() { return escapeHeightMetres; }
    public void setEscapeHeightMetres(Double v) { this.escapeHeightMetres = v; }
    public GeometryDoc getGeometry() { return geometry; }
    public void setGeometry(GeometryDoc geometry) { this.geometry = geometry; }
}
```

`app/src/main/java/nz/compliance/app/project/ProjectRepository.java`:
```java
package nz.compliance.app.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
}
```

`app/src/main/java/nz/compliance/app/project/FloorPlanRepository.java`:
```java
package nz.compliance.app.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FloorPlanRepository extends JpaRepository<FloorPlan, UUID> {
    List<FloorPlan> findByProjectId(UUID projectId);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=FloorPlanRepositoryIT`
Expected: PASS (Testcontainers spins up Postgres, Flyway migrates, jsonb round-trips). Requires Docker running.

- [ ] **Step 5: Commit**

```bash
git -C /workspace add app/src/main/java/nz/compliance/app/project app/src/test/java/nz/compliance/app/project/FloorPlanRepositoryIT.java
git -C /workspace commit -m "feat(app): Project/FloorPlan JPA entities with jsonb geometry + repositories"
```

---

## Task 5: Project REST API

**Files:**
- Create: `app/src/main/java/nz/compliance/app/project/dto/ProjectDto.java`
- Create: `app/src/main/java/nz/compliance/app/project/dto/CreateProjectRequest.java`
- Create: `app/src/main/java/nz/compliance/app/project/ProjectController.java`
- Test: `app/src/test/java/nz/compliance/app/project/ProjectControllerIT.java`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/nz/compliance/app/project/ProjectControllerIT.java`:
```java
package nz.compliance.app.project;

import nz.compliance.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProjectControllerIT extends PostgresIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void createThenListProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tower A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Tower A"));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tower A"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=ProjectControllerIT`
Expected: FAIL — controller/DTOs missing.

- [ ] **Step 3: Write the DTOs and controller**

`app/src/main/java/nz/compliance/app/project/dto/CreateProjectRequest.java`:
```java
package nz.compliance.app.project.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(@NotBlank String name) {
}
```

`app/src/main/java/nz/compliance/app/project/dto/ProjectDto.java`:
```java
package nz.compliance.app.project.dto;

import nz.compliance.app.project.Project;

import java.util.UUID;

public record ProjectDto(UUID id, String name) {
    public static ProjectDto from(Project p) {
        return new ProjectDto(p.getId(), p.getName());
    }
}
```

`app/src/main/java/nz/compliance/app/project/ProjectController.java`:
```java
package nz.compliance.app.project;

import jakarta.validation.Valid;
import nz.compliance.app.project.dto.CreateProjectRequest;
import nz.compliance.app.project.dto.ProjectDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projects;

    public ProjectController(ProjectRepository projects) {
        this.projects = projects;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@Valid @RequestBody CreateProjectRequest req) {
        return ProjectDto.from(projects.save(new Project(req.name())));
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projects.findAll().stream().map(ProjectDto::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectDto get(@PathVariable UUID id) {
        return projects.findById(id).map(ProjectDto::from)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
```

Add validation support — in `app/pom.xml` `<dependencies>`:
```xml
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=ProjectControllerIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git -C /workspace add app/pom.xml app/src/main/java/nz/compliance/app/project app/src/test/java/nz/compliance/app/project/ProjectControllerIT.java
git -C /workspace commit -m "feat(app): project REST API (create/list/get)"
```

---

## Task 6: FloorPlan REST API (create / get / save geometry)

**Files:**
- Create: `app/src/main/java/nz/compliance/app/project/dto/FloorPlanDto.java`
- Create: `app/src/main/java/nz/compliance/app/project/dto/SaveFloorPlanRequest.java`
- Create: `app/src/main/java/nz/compliance/app/project/FloorPlanController.java`
- Test: `app/src/test/java/nz/compliance/app/project/FloorPlanControllerIT.java`

- [ ] **Step 1: Write the failing test** (create a plan under a project, save geometry, reload it; invalid geometry → 422)

`app/src/test/java/nz/compliance/app/project/FloorPlanControllerIT.java`:
```java
package nz.compliance.app.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import nz.compliance.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class FloorPlanControllerIT extends PostgresIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private String createProject() throws Exception {
        String body = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"P\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    @Test
    void createSaveAndReloadFloorPlan() throws Exception {
        String projectId = createProject();

        String fpBody = mockMvc.perform(post("/api/projects/" + projectId + "/floorplans")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"L1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String fpId = json.readTree(fpBody).get("id").asText();

        String geometry = """
            {"name":"L1","riskGroup":"WB","sprinklered":true,"escapeHeightMetres":3.0,
             "geometry":{"schemaVersion":1,
               "spaces":[{"id":"s1","name":"Office","occupancyType":"WB",
                 "polygon":[{"x":0,"y":0},{"x":10,"y":0},{"x":10,"y":10},{"x":0,"y":10}]}],
               "doors":[{"id":"d1","fromSpaceId":"s1","toSpaceId":null,
                 "position":[{"x":0,"y":4},{"x":0,"y":6}],"clearWidthMillimetres":1200,"exit":true}]}}""";

        mockMvc.perform(put("/api/floorplans/" + fpId)
                        .contentType(MediaType.APPLICATION_JSON).content(geometry))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/floorplans/" + fpId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geometry.spaces[0].id").value("s1"))
                .andExpect(jsonPath("$.riskGroup").value("WB"));
    }

    @Test
    void rejectsInvalidGeometryWith422() throws Exception {
        String projectId = createProject();
        String fpBody = mockMvc.perform(post("/api/projects/" + projectId + "/floorplans")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"L1\"}"))
                .andReturn().getResponse().getContentAsString();
        String fpId = json.readTree(fpBody).get("id").asText();

        // space with only 2 points -> invalid
        String bad = """
            {"name":"L1","geometry":{"schemaVersion":1,
              "spaces":[{"id":"s1","name":"x","occupancyType":"WB",
                "polygon":[{"x":0,"y":0},{"x":1,"y":1}]}],"doors":[]}}""";

        mockMvc.perform(put("/api/floorplans/" + fpId)
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isUnprocessableEntity());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=FloorPlanControllerIT`
Expected: FAIL — controller/DTOs missing.

- [ ] **Step 3: Write the DTOs and controller**

`app/src/main/java/nz/compliance/app/project/dto/FloorPlanDto.java`:
```java
package nz.compliance.app.project.dto;

import nz.compliance.app.project.FloorPlan;
import nz.compliance.engine.model.GeometryDoc;

import java.util.UUID;

public record FloorPlanDto(UUID id, UUID projectId, String name, String riskGroup,
                           Boolean sprinklered, Double escapeHeightMetres, GeometryDoc geometry) {
    public static FloorPlanDto from(FloorPlan fp) {
        return new FloorPlanDto(fp.getId(), fp.getProjectId(), fp.getName(), fp.getRiskGroup(),
                fp.getSprinklered(), fp.getEscapeHeightMetres(), fp.getGeometry());
    }
}
```

`app/src/main/java/nz/compliance/app/project/dto/SaveFloorPlanRequest.java`:
```java
package nz.compliance.app.project.dto;

import jakarta.validation.constraints.NotBlank;
import nz.compliance.engine.model.GeometryDoc;

public record SaveFloorPlanRequest(@NotBlank String name, String riskGroup, Boolean sprinklered,
                                   Double escapeHeightMetres, GeometryDoc geometry) {
}
```

`app/src/main/java/nz/compliance/app/project/FloorPlanController.java`:
```java
package nz.compliance.app.project;

import jakarta.validation.Valid;
import nz.compliance.app.project.dto.FloorPlanDto;
import nz.compliance.app.project.dto.SaveFloorPlanRequest;
import nz.compliance.engine.model.GeometryDoc;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FloorPlanController {

    private final FloorPlanRepository floorPlans;
    private final ProjectRepository projects;

    public FloorPlanController(FloorPlanRepository floorPlans, ProjectRepository projects) {
        this.floorPlans = floorPlans;
        this.projects = projects;
    }

    @PostMapping("/projects/{projectId}/floorplans")
    @ResponseStatus(HttpStatus.CREATED)
    public FloorPlanDto create(@PathVariable UUID projectId, @Valid @RequestBody CreateFloorPlanBody body) {
        if (!projects.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "project not found");
        }
        return FloorPlanDto.from(floorPlans.save(new FloorPlan(projectId, body.name())));
    }

    @GetMapping("/projects/{projectId}/floorplans")
    public List<FloorPlanDto> list(@PathVariable UUID projectId) {
        return floorPlans.findByProjectId(projectId).stream().map(FloorPlanDto::from).toList();
    }

    @GetMapping("/floorplans/{id}")
    public FloorPlanDto get(@PathVariable UUID id) {
        return floorPlans.findById(id).map(FloorPlanDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/floorplans/{id}")
    public FloorPlanDto save(@PathVariable UUID id, @Valid @RequestBody SaveFloorPlanRequest req) {
        FloorPlan fp = floorPlans.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GeometryDoc geometry = req.geometry() == null ? new GeometryDoc(1, List.of(), List.of()) : req.geometry();
        List<String> errors = geometry.validationErrors();
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("; ", errors));
        }
        fp.setName(req.name());
        fp.setRiskGroup(req.riskGroup());
        fp.setSprinklered(req.sprinklered());
        fp.setEscapeHeightMetres(req.escapeHeightMetres());
        fp.setGeometry(geometry);
        return FloorPlanDto.from(floorPlans.save(fp));
    }

    public record CreateFloorPlanBody(@jakarta.validation.constraints.NotBlank String name) {
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl app test -Dtest=FloorPlanControllerIT`
Expected: PASS (both cases — happy path + 422).

- [ ] **Step 5: Commit**

```bash
git -C /workspace add app/src/main/java/nz/compliance/app/project app/src/test/java/nz/compliance/app/project/FloorPlanControllerIT.java
git -C /workspace commit -m "feat(app): floor-plan REST API with geometry validation"
```

---

## Task 7: Frontend API client + types

**Files:**
- Create: `frontend/src/api/types.ts`
- Create: `frontend/src/api/projects.ts`
- Create: `frontend/src/api/floorplans.ts`

- [ ] **Step 1: Mirror the backend types**

`frontend/src/api/types.ts`:
```ts
export interface Point { x: number; y: number }

export interface Space {
  id: string
  name: string
  occupancyType: string
  polygon: Point[]
}

export interface Door {
  id: string
  fromSpaceId: string
  toSpaceId: string | null
  position: Point[]            // exactly 2 points
  clearWidthMillimetres: number
  exit: boolean
}

export interface GeometryDoc {
  schemaVersion: number
  spaces: Space[]
  doors: Door[]
}

export interface Project { id: string; name: string }

export interface FloorPlan {
  id: string
  projectId: string
  name: string
  riskGroup: string | null
  sprinklered: boolean | null
  escapeHeightMetres: number | null
  geometry: GeometryDoc
}

export interface SaveFloorPlanRequest {
  name: string
  riskGroup: string | null
  sprinklered: boolean | null
  escapeHeightMetres: number | null
  geometry: GeometryDoc
}
```

- [ ] **Step 2: API helpers** (shared fetch wrapper + project/floorplan calls)

`frontend/src/api/projects.ts`:
```ts
import type { Project } from './types'

async function jsonFetch<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`)
  return (await res.json()) as T
}

export const api = { jsonFetch }

export const listProjects = () => jsonFetch<Project[]>('/api/projects')
export const createProject = (name: string) =>
  jsonFetch<Project>('/api/projects', { method: 'POST', body: JSON.stringify({ name }) })
```

`frontend/src/api/floorplans.ts`:
```ts
import { api } from './projects'
import type { FloorPlan, SaveFloorPlanRequest } from './types'

export const listFloorPlans = (projectId: string) =>
  api.jsonFetch<FloorPlan[]>(`/api/projects/${projectId}/floorplans`)

export const createFloorPlan = (projectId: string, name: string) =>
  api.jsonFetch<FloorPlan>(`/api/projects/${projectId}/floorplans`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })

export const getFloorPlan = (id: string) => api.jsonFetch<FloorPlan>(`/api/floorplans/${id}`)

export const saveFloorPlan = (id: string, req: SaveFloorPlanRequest) =>
  api.jsonFetch<FloorPlan>(`/api/floorplans/${id}`, { method: 'PUT', body: JSON.stringify(req) })
```

- [ ] **Step 3: Type-check**

Run: `cd /workspace/frontend && npx tsc -b`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git -C /workspace add frontend/src/api
git -C /workspace commit -m "feat(frontend): API client + shared geometry types"
```

---

## Task 8: The SVG floor-plan editor

This task builds the editor in three units: a pure geometry-state hook (`useFloorPlan`, fully unit-tested), the SVG canvas (`EditorCanvas`), and the toolbar/page wiring. Keep the hook pure (no React-DOM) so its logic is testable without rendering.

**Files:**
- Create: `frontend/src/editor/geometry.ts`
- Create: `frontend/src/editor/useFloorPlan.ts`
- Test: `frontend/src/editor/useFloorPlan.test.ts`
- Create: `frontend/src/editor/EditorCanvas.tsx`
- Test: `frontend/src/editor/EditorCanvas.test.tsx`
- Create: `frontend/src/editor/Toolbar.tsx`
- Create: `frontend/src/editor/EditorPage.tsx`
- Modify: `frontend/src/App.tsx` (render `EditorPage`)

- [ ] **Step 1: ID + geometry helpers**

`frontend/src/editor/geometry.ts`:
```ts
import type { GeometryDoc, Point, Space, Door } from '../api/types'

let counter = 0
export function nextId(prefix: string): string {
  counter += 1
  return `${prefix}${counter}`
}

export const emptyDoc = (): GeometryDoc => ({ schemaVersion: 1, spaces: [], doors: [] })

export function addSpace(doc: GeometryDoc, polygon: Point[], occupancyType = 'WB'): GeometryDoc {
  const id = nextId('s')
  const space: Space = { id, name: id, occupancyType, polygon }
  return { ...doc, spaces: [...doc.spaces, space] }
}

export function addDoor(
  doc: GeometryDoc,
  fromSpaceId: string,
  toSpaceId: string | null,
  position: Point[],
  clearWidthMillimetres = 1200,
  exit = false,
): GeometryDoc {
  const door: Door = { id: nextId('d'), fromSpaceId, toSpaceId, position, clearWidthMillimetres, exit }
  return { ...doc, doors: [...doc.doors, door] }
}

export function toggleExit(doc: GeometryDoc, doorId: string): GeometryDoc {
  return { ...doc, doors: doc.doors.map((d) => (d.id === doorId ? { ...d, exit: !d.exit } : d)) }
}
```

- [ ] **Step 2: Write the failing hook test**

`frontend/src/editor/useFloorPlan.test.ts`:
```ts
import { renderHook, act } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { useFloorPlan } from './useFloorPlan'

describe('useFloorPlan', () => {
  it('adds a space from drawn points', () => {
    const { result } = renderHook(() => useFloorPlan())
    act(() => result.current.commitSpace([
      { x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 },
    ]))
    expect(result.current.doc.spaces).toHaveLength(1)
    expect(result.current.doc.spaces[0].polygon).toHaveLength(4)
  })

  it('adds an exit door and toggles it', () => {
    const { result } = renderHook(() => useFloorPlan())
    act(() => result.current.commitSpace([
      { x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 },
    ]))
    const spaceId = result.current.doc.spaces[0].id
    act(() => result.current.commitDoor(spaceId, null, [{ x: 0, y: 4 }, { x: 0, y: 6 }], true))
    expect(result.current.doc.doors[0].exit).toBe(true)
    const doorId = result.current.doc.doors[0].id
    act(() => result.current.toggle(doorId))
    expect(result.current.doc.doors[0].exit).toBe(false)
  })
})
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd /workspace/frontend && npx vitest run src/editor/useFloorPlan.test.ts`
Expected: FAIL — `useFloorPlan` not found.

- [ ] **Step 4: Implement the hook**

`frontend/src/editor/useFloorPlan.ts`:
```ts
import { useCallback, useState } from 'react'
import type { GeometryDoc, Point } from '../api/types'
import { addDoor, addSpace, emptyDoc, toggleExit } from './geometry'

export interface FloorPlanState {
  doc: GeometryDoc
  setDoc: (doc: GeometryDoc) => void
  commitSpace: (polygon: Point[], occupancyType?: string) => void
  commitDoor: (from: string, to: string | null, position: Point[], exit?: boolean) => void
  toggle: (doorId: string) => void
}

export function useFloorPlan(initial: GeometryDoc = emptyDoc()): FloorPlanState {
  const [doc, setDoc] = useState<GeometryDoc>(initial)

  const commitSpace = useCallback(
    (polygon: Point[], occupancyType = 'WB') => setDoc((d) => addSpace(d, polygon, occupancyType)),
    [],
  )
  const commitDoor = useCallback(
    (from: string, to: string | null, position: Point[], exit = false) =>
      setDoc((d) => addDoor(d, from, to, position, 1200, exit)),
    [],
  )
  const toggle = useCallback((doorId: string) => setDoc((d) => toggleExit(d, doorId)), [])

  return { doc, setDoc, commitSpace, commitDoor, toggle }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd /workspace/frontend && npx vitest run src/editor/useFloorPlan.test.ts`
Expected: PASS.

- [ ] **Step 6: Implement the SVG canvas** (renders spaces/doors/exits; emits clicks in world coordinates)

`frontend/src/editor/EditorCanvas.tsx`:
```tsx
import type { GeometryDoc, Point } from '../api/types'

const SCALE = 20 // pixels per metre

interface Props {
  doc: GeometryDoc
  draft: Point[]                 // in-progress space polygon
  onCanvasClick: (worldPoint: Point) => void
}

export default function EditorCanvas({ doc, draft, onCanvasClick }: Props) {
  function handleClick(e: React.MouseEvent<SVGSVGElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    onCanvasClick({ x: (e.clientX - rect.left) / SCALE, y: (e.clientY - rect.top) / SCALE })
  }

  const poly = (pts: Point[]) => pts.map((p) => `${p.x * SCALE},${p.y * SCALE}`).join(' ')

  return (
    <svg
      width={800}
      height={600}
      role="img"
      aria-label="floor plan canvas"
      style={{ border: '1px solid #ccc', background: '#fafafa' }}
      onClick={handleClick}
    >
      {doc.spaces.map((s) => (
        <polygon key={s.id} points={poly(s.polygon)} fill="#e8f0fe" stroke="#3367d6" />
      ))}
      {doc.doors.map((d) => (
        <line
          key={d.id}
          x1={d.position[0].x * SCALE} y1={d.position[0].y * SCALE}
          x2={d.position[1].x * SCALE} y2={d.position[1].y * SCALE}
          stroke={d.exit ? '#0b8043' : '#999'} strokeWidth={4}
        />
      ))}
      {draft.length > 0 && (
        <polyline points={poly(draft)} fill="none" stroke="#ff6d00" strokeDasharray="4" />
      )}
    </svg>
  )
}
```

- [ ] **Step 7: Write the failing canvas test** (renders saved geometry)

`frontend/src/editor/EditorCanvas.test.tsx`:
```tsx
import { render } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import EditorCanvas from './EditorCanvas'
import type { GeometryDoc } from '../api/types'

const doc: GeometryDoc = {
  schemaVersion: 1,
  spaces: [{ id: 's1', name: 'Office', occupancyType: 'WB',
    polygon: [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 }] }],
  doors: [{ id: 'd1', fromSpaceId: 's1', toSpaceId: null,
    position: [{ x: 0, y: 4 }, { x: 0, y: 6 }], clearWidthMillimetres: 1200, exit: true }],
}

describe('EditorCanvas', () => {
  it('renders one polygon and one (exit) door line', () => {
    const { container } = render(<EditorCanvas doc={doc} draft={[]} onCanvasClick={vi.fn()} />)
    expect(container.querySelectorAll('polygon')).toHaveLength(1)
    const line = container.querySelector('line')
    expect(line).not.toBeNull()
    expect(line!.getAttribute('stroke')).toBe('#0b8043') // exit colour
  })
})
```

- [ ] **Step 8: Run the canvas test (it passes against Step 6)**

Run: `cd /workspace/frontend && npx vitest run src/editor/EditorCanvas.test.tsx`
Expected: PASS.

- [ ] **Step 9: Toolbar + page wiring** (modes: draw space / add exit door; save)

`frontend/src/editor/Toolbar.tsx`:
```tsx
export type Mode = 'space' | 'exitDoor'

interface Props {
  mode: Mode
  onMode: (m: Mode) => void
  onFinishSpace: () => void
  onSave: () => void
  saving: boolean
}

export default function Toolbar({ mode, onMode, onFinishSpace, onSave, saving }: Props) {
  return (
    <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
      <button aria-pressed={mode === 'space'} onClick={() => onMode('space')}>Draw space</button>
      <button aria-pressed={mode === 'exitDoor'} onClick={() => onMode('exitDoor')}>Add exit door</button>
      <button onClick={onFinishSpace} disabled={mode !== 'space'}>Finish space</button>
      <button onClick={onSave} disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
    </div>
  )
}
```

`frontend/src/editor/EditorPage.tsx`:
```tsx
import { useEffect, useState } from 'react'
import type { Point } from '../api/types'
import { getFloorPlan, saveFloorPlan } from '../api/floorplans'
import { useFloorPlan } from './useFloorPlan'
import EditorCanvas from './EditorCanvas'
import Toolbar, { type Mode } from './Toolbar'

interface Props { floorPlanId: string }

export default function EditorPage({ floorPlanId }: Props) {
  const fp = useFloorPlan()
  const [mode, setMode] = useState<Mode>('space')
  const [draft, setDraft] = useState<Point[]>([])
  const [name, setName] = useState('Level 1')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    getFloorPlan(floorPlanId).then((loaded) => {
      fp.setDoc(loaded.geometry)
      setName(loaded.name)
    }).catch(() => { /* new/unsaved plan */ })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [floorPlanId])

  function onCanvasClick(p: Point) {
    if (mode === 'space') {
      setDraft((d) => [...d, p])
    } else if (mode === 'exitDoor' && fp.doc.spaces.length > 0) {
      // attach exit door to the most-recent space at the clicked point (0.5 m segment)
      const space = fp.doc.spaces[fp.doc.spaces.length - 1]
      fp.commitDoor(space.id, null, [{ x: p.x, y: p.y - 0.5 }, { x: p.x, y: p.y + 0.5 }], true)
    }
  }

  function finishSpace() {
    if (draft.length >= 3) fp.commitSpace(draft)
    setDraft([])
  }

  async function save() {
    setSaving(true)
    try {
      await saveFloorPlan(floorPlanId, {
        name, riskGroup: 'WB', sprinklered: true, escapeHeightMetres: 3, geometry: fp.doc,
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <main>
      <h1>compliance-checker — editor</h1>
      <label>Plan name <input value={name} onChange={(e) => setName(e.target.value)} /></label>
      <Toolbar mode={mode} onMode={setMode} onFinishSpace={finishSpace} onSave={save} saving={saving} />
      <EditorCanvas doc={fp.doc} draft={draft} onCanvasClick={onCanvasClick} />
      <p>Spaces: {fp.doc.spaces.length} · Doors: {fp.doc.doors.length}</p>
    </main>
  )
}
```

- [ ] **Step 10: Render the editor from App** — replace `frontend/src/App.tsx`:
```tsx
import { useEffect, useState } from 'react'
import { createProject } from './api/projects'
import { createFloorPlan } from './api/floorplans'
import EditorPage from './editor/EditorPage'

export default function App() {
  const [floorPlanId, setFloorPlanId] = useState<string | null>(null)

  useEffect(() => {
    // Bootstrap a project + floor plan for the demo. (Project/plan pickers come later.)
    createProject('Demo project')
      .then((p) => createFloorPlan(p.id, 'Level 1'))
      .then((fp) => setFloorPlanId(fp.id))
      .catch((e) => console.error(e))
  }, [])

  if (!floorPlanId) return <p>Starting…</p>
  return <EditorPage floorPlanId={floorPlanId} />
}
```

- [ ] **Step 11: Run all frontend tests**

Run: `cd /workspace/frontend && npm run test`
Expected: PASS (App, useFloorPlan, EditorCanvas). Note: the old `App.test.tsx` from Plan 1 tested the health screen — delete it (`rm src/App.test.tsx`) since `App` now bootstraps the editor.

- [ ] **Step 12: Manual end-to-end check**

Start DB + backend + frontend:
```bash
cd /workspace && docker compose up -d db
DB_URL=jdbc:postgresql://localhost:5432/compliance ./mvnw -B -pl app spring-boot:run   # shell 1
cd /workspace/frontend && npm run dev                                                  # shell 2
```
In the browser (`http://localhost:5173`): draw a space (click ≥3 points → Finish space), switch to **Add exit door** and click on an edge, then **Save**. Reload the page — the saved geometry should re-render.

- [ ] **Step 13: Commit**

```bash
git -C /workspace add frontend/src/editor frontend/src/App.tsx
git -C /workspace rm frontend/src/App.test.tsx
git -C /workspace commit -m "feat(frontend): SVG floor-plan editor (draw spaces, exit doors, save/load)"
```

---

## Definition of done (Plan 2)

- `./mvnw verify` green (engine model tests + app Testcontainers ITs); `npm run test` green.
- Local stack runs: draw a plan, save it, reload, and it persists (jsonb round-trip).
- Invalid geometry (e.g. a 2-point space) is rejected with HTTP 422.

## Self-review notes

- **Spec coverage:** Project/FloorPlan/BuildingContext + typed `geometryJson` (spec §6) ✓; geometry schema Space/Door/Exit (§6) ✓; CRUD + validation (§8, §11 "reject at input") ✓; editor (§3, §5) ✓. Engine consumption of the model is Plan 3+; async checks Plan 5.
- **Placeholders:** none — all code complete. The exit-door interaction is intentionally simplified (fixed 0.5 m segment at the click); richer edge-snapping is a later refinement, noted, not a gap.
- **Type consistency:** `GeometryDoc{schemaVersion,spaces,doors}`, `Space{id,name,occupancyType,polygon}`, `Door{id,fromSpaceId,toSpaceId,position,clearWidthMillimetres,exit}` are identical across engine Java records, app DTO JSON, and the frontend `types.ts`. Endpoints (`/api/projects`, `/api/projects/{id}/floorplans`, `/api/floorplans/{id}`) match between controllers and the frontend API client.
```
