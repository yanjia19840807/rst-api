# RST API

Spring Boot API for the Right Sizing Tool.

## Requirements

- Java 21
- PostgreSQL 14+ (local install)

## Local development

### Database

Connect to local PostgreSQL. JDBC URL, username and password come from
environment variables (local file: `.env`, gitignored). Copy `.env.example`
to `.env` and fill `DB_PASSWORD`.

| Variable | Example |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/rst` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | local Postgres password |
| Dev identity CCGID | `app.security.dev-identity.ccgid` in `application-dev.yml` |

Create database `rst` if needed. This project does **not** use Docker Compose / Testcontainers.

### Run API

```sh
# defaults to profile dev → loads application-dev.yml
./mvnw spring-boot:run
```

IDE Run Configuration: leave Active profiles empty — `application.yml` already defaults to `dev`.

### Dev identity (simulate one user login)

Turn on one switch. The person is chosen in the SPA, not in YAML.
Requests without a person default to `ADMIN001` / `ADMIN`.

```yaml
app:
  security:
    dev-identity:
      override-enabled: true
```

- URL: `?ccgid=S00813982&role=SUPERVISOR` (optional `&center=GBS%20CHINA%20INDIA`)
- Headers: `X-Dev-Ccgid`, `X-Dev-Role`, `X-Dev-Center`

Display name comes from the ACTIVE Daily Timesheet (or `Dev User <ccgid>`).
Identity is the CCGID only — no local `app_user` row. Keep the switch
`false` after SSO is connected.

- API: `http://localhost:8080/api/v1`
- Health: `http://localhost:8080/actuator/health`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

To connect `rst-web`, set `VITE_API_BASE_URL=http://localhost:8080` (or leave it empty and use the Vite `/api` proxy).

## Configuration

Runtime profiles are **dev** (default, local Dev Identity), **uat**, **pre**, and **prod**.
JUnit uses `application-test.yml` only.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/rst` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | empty | Database password; local `.env`, never commit |
| `CORS_ALLOWED_ORIGINS` | localhost / host per profile | Comma-separated SPA origins |
| `AZURE_TENANT_ID` | CMA CGM tenant | Entra tenant; required for SSO (uat/pre/prod) and Graph |
| `AZURE_CLIENT_ID` | profile default | RST Azure app; not used on local `dev` |
| `AZURE_CLIENT_SECRET` | empty | RST Azure secret; env only, never commit |
| `AZURE_REDIRECT_URI` | `{host}/api/sso/callback` | Registered confidential-client redirect |
| `SSO_ENV` | UAT / PRE / PROD | Must match `CMACGM_APP_RST_{NAME}_{ENV}` |
| `MS_GRAPH_CLIENT_ID` | empty (dev default set) | Graph application (client) id |
| `MS_GRAPH_CLIENT_SECRET` | empty | Graph client secret; never commit |
| `TIMESHEET_SHAREPOINT_ROOT` | `4.RST/2.UAT` (`4.RST/2.PRE` in pre, `4.RST/3.Production` in prod) | Timesheet library RST folder |
| `FORECAST_BASE_URL` | `http://localhost:8000` | Python forecast service |
| `FORECAST_ENABLED` | `true` | Enable forecast HTTP calls |
| `MAIL_ENABLED` | `true` in runtime profiles | Send workflow / Timesheet-fail mail |
| `MAIL_REDIRECT_TO` | empty | Redirect all outgoing mail to this inbox |
| `MAIL_FROM` | `GBS.TIMESHEET@cma-cgm.com` | Graph send-as mailbox |

uat / pre / prod authenticate with Azure authorization-code + HttpOnly session.
Local `dev` uses Dev Identity (`X-Dev-Role` / query). Do not put secrets in YAML.

## Architecture

Code is grouped by business capability. Each feature uses this dependency direction:

```text
api -> application -> domain/persistence
```

- `common`: ProblemDetail errors and shared paging.
- `config` / `security`: CORS, Dev Identity or Azure SSO session, OpenAPI, clock and principals.
- `identity`: application users.
- `graph`: Microsoft Graph client-credentials client for the Timesheet SharePoint library.
- `timesheet`: Daily org + Monthly KPI snapshots, Toolkit hierarchy, and Shared KPI candidates.
- `toolkit`: Supervisor CRUD and Agent dynamic Toolkit access.
- `exercise`: immutable Toolkit/Subtask/KPI/HC snapshots.
- `tms`: timing session state machine, persistence and REST API.

Controllers do not expose JPA entities. Public list endpoints use 1-based page numbers even though
Spring Data uses 0-based pages internally.

## Key endpoints

- `GET /api/v1/toolkits` — Toolkits available to the current principal
- `GET /api/v1/toolkits/managed` — Toolkits the current principal can manage
- `POST /api/v1/toolkits` and `GET|PUT|DELETE /api/v1/toolkits/{id}`
- `GET /api/v1/timesheet/active` — `{ org, kpi }` Daily and Monthly ACTIVE headers
- `GET /api/v1/timesheet/toolkit-hierarchy`
- `GET /api/v1/timesheet/shared-kpi-candidates` — Monthly KPI rows; `syncDate` is the Monthly date
- `GET|POST /api/v1/exercises`
- `GET /api/v1/exercises/{id}`
- `GET /api/v1/tms/summary`
- `GET /api/v1/tms/sessions/current`
- `GET /api/v1/tms/sessions`
- `GET /api/v1/tms/team/agents`
- `GET /api/v1/tms/team/sessions`
- `GET /api/v1/tms/team/sessions/{id}`
- `POST /api/v1/tms/sessions`
- `POST /api/v1/tms/sessions/{id}/pause`
- `POST /api/v1/tms/sessions/{id}/resume`
- `POST /api/v1/tms/sessions/{id}/end`
- `POST /api/v1/tms/sessions/{id}/discard`

The database and application enforce at most one `RUNNING` session per Agent. Multiple `PAUSED`
sessions are allowed; start and resume are blocked only while another session is already running.
Starting a session revalidates Agent access against ACTIVE Daily assignment
(`supervisor_position` + `pl3`). Existing sessions remain operable if the next
Daily snapshot changes access; their display fields are historical snapshots.
Discard is a state transition and never physically deletes TMS history.

Flyway V3 is incremental: V1/V2 remain unchanged. It adds Timesheet snapshots, permanent
Supervisor Position + PL3 Toolkit identity, Shared KPI selection, Exercise snapshots and the revised
TMS history model. Legacy demo Toolkit rows receive deterministic placeholder business keys so
existing V2 session foreign keys survive the upgrade. V49 replaces the raw
`timesheet_snapshot_row` with Daily org tables and a Monthly KPI table; each
`kind` (`DAILY` / `MONTHLY`) has its own ACTIVE `timesheet_sync_run`.

## Timesheet sync (dev / ops)

Daily and Monthly reports are two sources. Daily writes `timesheet_person` from
every complete employee row and `timesheet_position` from Production +
Productive rows whose PL3 is RST-applicable in GBS Process. Monthly writes `timesheet_scope`,
`timesheet_assignment`, and Delivery HC for the same RST-applicable PL3 codes.
Each kind keeps one ACTIVE run; a failed run does not replace the previous
ACTIVE.

Automatic sync picks the file whose **name** has the latest business date
(`Daily Report of yyyyMMdd(GBS CHINA).xlsx` /
`Monthly Report of yyyyMM(GBS CHINA).xlsx`). Two files on the same date fail
with `AMBIGUOUS_SOURCE`. LTH uploads land in `{root}/Manual` and activate
immediately when valid; they do not pause the Quartz schedule.

Recurring sync uses Quartz when `timesheet.sync.daily.enabled` or
`timesheet.sync.monthly.enabled` is true. Cron is system configuration only.

| Property | Default | Purpose |
| --- | --- | --- |
| `timesheet.sharepoint.site` | `…/sites/CMA-SharedKPIAutomation` | Timesheet document-library site |
| `timesheet.sharepoint.library` | `Timesheet` | Document library display name |
| `timesheet.sharepoint.root` | `4.RST/2.UAT` / `4.RST/2.PRE` / `4.RST/3.Production` | RST folder (`Daily`, `Monthly`, `Manual`, `Template`) |
| `timesheet.sync.daily.enabled` | `false` | Register Daily Quartz job |
| `timesheet.sync.daily.cron` | `0 0 6 * * ?` | Daily Quartz cron |
| `timesheet.sync.monthly.enabled` | `false` | Register Monthly Quartz job |
| `timesheet.sync.monthly.cron` | `0 30 6 * * ?` | Monthly Quartz cron |
| `process.remote` | `false` (`true` in pre / prod) | GBS Process from SharePoint list when true |
| `process.classpath-location` | `timesheet/GBS Process.csv` | Local catalog; ignored when `remote=true` |
| `process.sharepoint.site` | `…/sites/CMA-GlobalBusinessServices` | Site that hosts the GBS Process list |
| `process.sharepoint.list` | `GBS Process` | SharePoint list display name |

Same `driveItemId` + `etag` on the same business date, or the same content
hash, skips cutover. An older filename date than the ACTIVE snapshot is also
skipped. Required-field and `DATE_MISMATCH` issues fail the run. Old ACTIVE
headers are archived, not deleted.

## Verification

```sh
./mvnw verify
```

Unit and H2-backed API tests always run.
