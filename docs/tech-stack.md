# Tech stack (locked)

Decisions for v1 of the personal maintenance planner. Functional specs: [requirements.md](./requirements.md), [scheduling-model.md](./scheduling-model.md), [pain-model.md](./pain-model.md), [planning-algorithm.md](./planning-algorithm.md).

---

## Summary

| Layer | Choice |
|-------|--------|
| Language (server) | **Java 21** (LTS) |
| Framework | **Spring Boot 3.4+** |
| API | **REST**, JSON, **springdoc-openapi** |
| Persistence | **Spring Data JPA**, **Flyway**, **H2** (file mode) |
| Server tests | **JUnit 6** (Jupiter), **AssertJ**, **Mockito** (via starter) |
| Frontend | **React**, **TypeScript**, **Vite** |
| Client data | **TanStack Query** |
| UI styling | **Tailwind CSS** + **Radix UI** (or similar headless primitives) |
| Dates (UI) | **date-fns** |
| Build | **Gradle** (Kotlin DSL), monorepo |
| Packaging | Single Spring Boot JAR serves API + static frontend in prod-like runs |

---

## Runtime and deployment

- **Single user**, bind **localhost** only in v1.
- **No authentication** in v1.
- One process: Spring Boot embedded Tomcat.
- Data: H2 file under user home or configured path (backup = copy file).

---

## Repository layout

```
/
  backend/          # Spring Boot application
  frontend/         # Vite React app
  docs/             # requirements, scheduling, pain, tech-stack
  build.gradle.kts  # optional root aggregator
```

Dev: Vite dev server with proxy to `http://localhost:8080` (or chosen port).

Prod-like local: `frontend` build output copied to `backend/src/main/resources/static`.

---

## Backend structure (planned)

```
com...maintainance
  domain/           # Task, Instance, Rules, SchedulingService, PlanningService
  persistence/      # JPA entities, repositories
  api/              # REST controllers, DTOs
  config/           # H2, CORS for localhost, script runner for due()
```

- **Domain logic** in plain Java (no JPA in scheduling/planning core).
- **MapStruct** optional for entity ↔ DTO mapping.
- **Due function:** `ProcessBuilder` + timeout; boolean stdout or exit code contract TBD.

---

## JUnit 6

- Use **JUnit Jupiter 6.x** (e.g. **6.1.0**).
- In `backend/build.gradle.kts`:

```kotlin
extra["junit-jupiter.version"] = "6.1.0"

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

- Do **not** import `org.junit:junit-bom` alongside Spring Boot BOM.
- Prefer **unit tests** for scheduling and planning; `@DataJpaTest` / `@WebMvcTest` for slices.

---

## Database

- **H2** `MODE=PostgreSQL` or default; **Flyway** migrations in `backend/src/main/resources/db/migration`.
- Schema sketch (implementation detail, not frozen here):
  - `task`, `task_rules` (JSON column acceptable in v1)
  - `instance` (open), `completion`
  - `settings` (global pain / daily caps)

---

## Frontend

- **React 19** + **TypeScript** + **Vite**.
- **TanStack Query** for plan horizon and task list.
- Views (requirements):
  - Task list / editor (rules editor, archived)
  - Plan week / month (mark done, snooze, interval ±% when applicable)
  - Settings (pain curve, caps, P*)

---

## API (initial, coarse)

| Method | Path | Purpose |
|--------|------|---------|
| GET/POST/PUT/DELETE | `/api/tasks` | Task CRUD |
| GET | `/api/tasks/{id}/completions` | History |
| POST | `/api/plan` | Body: `{ horizonStart, horizonEnd }` → **PlanResult**; each **`items[]`** row includes **`timingPain`** ([planning-algorithm.md](./planning-algorithm.md) §10.1) |
| POST | `/api/instances/{id}/complete` | Mark done; optional interval delta % |
| POST | `/api/instances/{id}/snooze` | Snooze until |
| GET/PUT | `/api/settings` | Global planner settings |

OpenAPI UI at `/swagger-ui.html` (springdoc).

---

## Out of scope for stack v1

- PostgreSQL / multi-environment (can swap later via config)
- Docker / K8s
- Spring Security
- OptaPlanner or external CP solver
- Mobile app
- Real-time / WebSockets

---

## Changelog

| Version | Notes |
|---------|--------|
| 1.0 | Initial locked stack |
