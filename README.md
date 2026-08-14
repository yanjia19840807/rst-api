# RST API

Spring Boot API for the Right Sizing Tool.

## Requirements

- Java 21
- PostgreSQL 14+ (local install)

## Local development

### Database

Connect to local PostgreSQL. Credentials and walkthrough identity live in
`src/main/resources/application-dev.yml` (default profile is `dev`).

| Setting | Value in `application-dev.yml` |
| --- | --- |
| JDBC URL | `jdbc:postgresql://localhost:5432/rst` |
| Username | `postgres` |
| Password | set in `application-dev.yml` |
| Dev identity CCGID | `app.security.dev-identity.ccgid` |

Create database `rst` if needed. This project does **not** use Docker Compose / Testcontainers.

### Run API

```sh
# defaults to profile dev → loads application-dev.yml
./mvnw spring-boot:run
```

IDE Run Configuration: leave Active profiles empty — `application.yml` already defaults to `dev`.

### Dev identity (simulate one user login)

In `application-dev.yml` set only `ccgid` + `role`:

```yaml
app:
  security:
    dev-identity:
      ccgid: S00628182   # Timesheet person, or any CCGID for assumed LTH/HO
      role: SUPERVISOR   # AGENT | SUPERVISOR | MANAGER | CDH | LTH | HO
```

On first API call the backend ensures an `app_user` row exists (from Timesheet name when
available, otherwise a synthetic display name). Optional request overrides:
`X-Dev-Ccgid`, `X-Dev-Role`. Restart the API after changing config.

- API: `http://localhost:8080/api/v1`
- Health: `http://localhost:8080/actuator/health`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

To connect `rst-web`, set `VITE_API_BASE_URL=http://localhost:8080` and
`VITE_ENABLE_MSW=false`.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | from `application-dev.yml` / env | PostgreSQL JDBC URL |
| `DB_USERNAME` | from `application-dev.yml` / env | Database user |
| `DB_PASSWORD` | from `application-dev.yml` / env | Database password |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated SPA origins |
| `AZURE_TENANT_ID` | empty | Required outside dev/test |

Production validates Azure JWTs. Authorization is derived from the authenticated principal; API
requests cannot select another user's scope.

## Architecture

Code is grouped by business capability. Each feature uses this dependency direction:

```text
api -> application -> domain/persistence
```

- `common`: ProblemDetail errors and shared paging.
- `config` / `security`: CORS, OAuth2 Resource Server, OpenAPI, clock and principals.
- `identity`: application users.
- `timesheet`: read-only ACTIVE snapshot, hierarchy, KPI and headcount queries.
- `toolkit`: Supervisor CRUD and Agent dynamic Toolkit access.
- `exercise`: immutable Toolkit/Subtask/KPI/HC snapshots.
- `tms`: timing session state machine, persistence and REST API.

Controllers do not expose JPA entities. Public list endpoints use 1-based page numbers even though
Spring Data uses 0-based pages internally.

## Key endpoints

- `GET /api/v1/toolkits`
- `GET|POST /api/v1/supervisor/toolkits`
- `GET|PUT|DELETE /api/v1/supervisor/toolkits/{id}`
- `GET /api/v1/timesheet/active`
- `GET /api/v1/timesheet/toolkit-hierarchy`
- `GET /api/v1/timesheet/shared-kpi-candidates`
- `GET /api/v1/timesheet/countries`
- `GET /api/v1/timesheet/kpis`
- `GET /api/v1/timesheet/headcount`
- `GET|POST /api/v1/supervisor/exercises`
- `GET /api/v1/supervisor/exercises/{id}`
- `GET /api/v1/tms/summary`
- `GET /api/v1/tms/sessions/current`
- `GET /api/v1/tms/sessions`
- `POST /api/v1/tms/sessions`
- `POST /api/v1/tms/sessions/{id}/pause`
- `POST /api/v1/tms/sessions/{id}/resume`
- `POST /api/v1/tms/sessions/{id}/end`
- `POST /api/v1/tms/sessions/{id}/discard`

The database and application enforce at most one combined `RUNNING`/`PAUSED` session per Agent.
Starting a session revalidates Agent access against the ACTIVE Timesheet snapshot. Existing sessions
remain operable if the next Timesheet snapshot changes access; their display fields are historical
snapshots. Discard is a state transition and never physically deletes TMS history.

Flyway V3 is incremental: V1/V2 remain unchanged. It adds Timesheet snapshots, permanent
Supervisor Position + PL3 Toolkit identity, Shared KPI selection, Exercise snapshots and the revised
TMS history model. Legacy demo Toolkit rows receive deterministic placeholder business keys so
existing V2 session foreign keys survive the upgrade.

## Timesheet sync (dev / ops)

Load a Monthly Report Excel as a full snapshot and activate it:

```sh
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--timesheet.sync.enabled=true --timesheet.sync.date=2026-06-30 --server.port=0"
```

Defaults:

| Property | Default |
| --- | --- |
| `timesheet.sync.file` | `classpath:timesheet/Monthly Report of 202606(GBS CHINA Mock).xlsx` |
| `timesheet.sync.sheet` | `Mock Data` |
| `timesheet.sync.date` | today (UTC) |

Flow: parse/validate Excel → insert `timesheet_sync_run` (`LOADING`) + `timesheet_snapshot_row` → archive previous `ACTIVE` → mark new run `ACTIVE`. On failure the new run becomes `FAILED` and the previous `ACTIVE` snapshot is kept.

## Verification

```sh
./mvnw verify
```

Unit and H2-backed API tests always run.
