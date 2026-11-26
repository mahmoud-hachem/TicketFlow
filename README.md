# TicketFlow

TicketFlow is a completed six-feature support-ticket MVP for the Agentic Software Engineering course. React and Spring Boot provide ticket workflows backed by MongoDB Atlas. Human engineering decisions, AI contributions and verification evidence are recorded separately from historical design proposals.

## Features

- Create tickets with title, description, requester name and LOW/MEDIUM/HIGH priority.
- List tickets newest first and view details, assignment and chronological comments.
- Assign or reassign one existing support agent.
- Progress OPEN → IN_PROGRESS → RESOLVED → CLOSED, with RESOLVED → IN_PROGRESS reopening. IN_PROGRESS requires an assignee; CLOSED tickets reject changes.
- Add comments with author names, embedded in their ticket.
- Reject stale browser versions and competing writes with HTTP 409; recovery requires explicit reload/retry.

Priority is fixed at creation. Names are display values, not authenticated identities. Login, permission enforcement, email, attachments, search/filtering, pagination, analytics, deletion and agent administration are outside the MVP.

## Stack and architecture

| Layer | Implementation |
|---|---|
| Frontend | React, Vite, JavaScript; relative `/api` requests |
| Backend | Java 21 compilation target, Spring Boot 4.2, Maven |
| Structure | One backend, feature-oriented `ticket`/`agent` packages, shared `config` |
| API | Seven REST endpoints, DTOs, JSON and RFC 9457 Problem Details |
| Persistence | Spring Data MongoDB; Atlas `tickets` and `agents` collections |
| Concurrency | Client `expectedVersion` precondition plus persistence-managed `@Version` |

Controllers validate HTTP input; services coordinate repositories, IDs, timestamps and cross-record checks; the Ticket domain object protects workflow invariants. Comments are embedded, agents referenced by ID, UUIDs stored as strings and timestamps stored as BSON dates. Only default `_id` indexes are planned for this small dataset.

Vite forwards `/api` to `http://127.0.0.1:8080` during development; production routing is not defined. See the [architecture](docs/design/SYSTEM_ARCHITECTURE.md), [UML](docs/design/OBJECT_ORIENTED_DESIGN.md), [data model](docs/design/MONGODB_DATA_MODEL.md) and [API contract](docs/design/REST_API_CONTRACT.md).

## Setup on Windows

Prerequisites: Java 21 or a compatible later JDK, Node.js 24, npm, and authorized MongoDB Atlas credentials/network access. Maven Wrapper is included; installed Maven may also be used. Never put credentials in commands, Git, screenshots or reports. A root `.env` file is not automatically loaded by these startup commands; configure the launching process environment explicitly.

From the repository root, start the backend in a private PowerShell session:

```powershell
Set-Location .\backend
$mongoUriSecret = Read-Host 'Authorized application MongoDB URI' -AsSecureString
$mongoUriPointer = [IntPtr]::Zero
try {
    $mongoUriPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($mongoUriSecret)
    $env:MONGODB_URI = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($mongoUriPointer)
    & .\mvnw.cmd spring-boot:run
}
finally {
    if ($mongoUriPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($mongoUriPointer)
    }
    $mongoUriSecret.Dispose()
    Remove-Item Env:MONGODB_URI -ErrorAction SilentlyContinue
}
```

The backend binds to loopback on port 8080. Use a separately authorized application/demo database; integration tests retain records and require an empty isolated target. The URI necessarily exists in process memory while the application runs.

In a second terminal, from the repository root:

```powershell
Set-Location .\frontend
npm ci
npm run dev
```

Open the local URL printed by Vite (normally `http://localhost:5173`). Both processes must run for the real application.

### Demo agents

Seeding is disabled by default. In the backend command above, replace the startup line with the following only for an authorized demo database:

```powershell
& .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--ticketflow.demo-seeding.enabled=true'
```

Startup inserts only missing Demo Agent 1–3 records using fixed UUIDs. It preserves edited agents, never seeds tickets and never deletes records. Omit the flag on subsequent starts to disable seeding; existing records remain. `GET /api/agents` returns IDs and names, ordered by name then ID, without emails.

## Testing and verified results

Default backend tests are isolated: `BackendApplicationTests` and `*IT` are excluded. From the repository root:

```powershell
Set-Location .\backend
& .\mvnw.cmd test
```

From `frontend`, run `npm run lint` and `npm run build`. The separate mocked browser script and quality commands are documented in [CODE_QUALITY.md](docs/CODE_QUALITY.md); its external Playwright/Edge installation is not a project-managed dependency.

| Evidence | Recorded result |
|---|---|
| After quality refactoring, before IT-01 | 303 isolated tests passed |
| After IT-01, reported by Codex | 311 isolated tests passed; zero failures/errors/skips |
| Real MongoDB integration, human-verified | 7 integration tests + 1 application context test passed; zero failures/errors/skips; BUILD SUCCESS on 26 November 2026 at 22:39, duration 35.114 seconds |
| Real browser demonstration, human-verified | Creation, list, details, assignment, reassignment, status progression and CLOSED protection verified |
| Comments | Manual demonstration intentionally omitted; covered by automated tests and the real integration suite |
| Quality baseline, measured before IT-01 | Java line coverage 97.87%, branch coverage 93.29%; Java complexity 211 → 202; frontend complexity 316 → 282 |

These are recorded results, not fresh runs during documentation finalization. Coverage is the pre-IT-01 isolated quality measurement, not combined integration coverage. Earlier frontend lint/build and 16 mocked browser scenario groups passed; mocked results are distinct from the real browser demonstration.

Real database tests require explicit authorization, exact target confirmation, matching URI/database, majority writes, primary reads and empty tickets/agents. IT-01 permits the exact owner-authorized `ticketflow_test` alongside separately authorized generated isolated names; `ticketflow_dev` remains rejected. Follow [TIC-10 verification](docs/TIC10_VERIFICATION.md) for secure PowerShell configuration and the separate `mongo-integration` profile. Tests retain records: do not delete, drop or reset data to rerun them.

## Documentation and delivery scope

- [Requirements and acceptance criteria](docs/REQUIREMENTS.md)
- [ADR-001: architecture and database alternatives](docs/decisions/ADR-001.md)
- [Phase 2 decisions D1–D25 and corrections R1–R4](docs/PHASE2_ENGINEERING_DECISIONS.md)
- [Phase 2 design review](docs/design/DESIGN_REVIEW.md)
- [TIC-10 results and authentication troubleshooting](docs/TIC10_VERIFICATION.md)
- [Quality measurements before IT-01](docs/CODE_QUALITY.md)
- [Human–AI development record and lessons](docs/AGENTIC_DEVELOPMENT.md)

Phase 2 artifacts preserve their 19 November design context; future-tense statements are historical. Current implementation and results are summarized here and in TIC-10 verification. Original planning used two epics, six feature stories (`TIC-3`–`TIC-8`) and two technical tasks (`TIC-9`, `TIC-10`), with provisional sprint estimates of 18 and 19 person-hours. These are not actual effort measurements or claims about current Jira status or teammate approval.

## Limitations

The unauthenticated API is for local development and controlled academic demonstration, with no public production deployment or verified user identity. Atlas requires network access and securely supplied credentials. Large datasets, unbounded comment growth, load/performance, production hosting and comprehensive browser/device coverage are not established by the recorded tests. No pagination, custom indexes or migration system is supplied. High coverage and lower complexity do not prove correctness; the frontend still has a large App component and 15 state hooks. Assessment, Jira and teammate review evidence must be recorded by responsible humans rather than inferred from test success.
