# TIC-10 verification and pending database approval

## Review and coverage

The reviewed baseline is REQUIREMENTS.md, ADR-001, D1–D25, R1–R4 and I6-01–I6-12. Design files were interpreted by internal title: SYSTEM_ARCHITECTURE (1) contains the API contract; REST_API_CONTRACT (1) contains the UML; OBJECT_ORIENTED_DESIGN (1) contains the database model; MONGODB_DATA_MODEL (1) duplicates DESIGN_REVIEW. Those files remain unchanged.

| Requirements | Existing isolated evidence | Additional evidence prepared |
|---|---|---|
| FR-01, BR-01–03, R2 | Ticket create/service/controller validation | All priorities, exact trimmed maximums and every blank/over-limit field; real create/read/BSON dates/string IDs/version mapping |
| FR-02–03, BR-10 | Empty lists, service order preservation, details, assignee/comment mapping, 404 | Actual database date/ID sorting, persistence across fresh repository reads, embedded comment ordering |
| FR-04, BR-04–05, D19 | Assignment/reassignment/same-agent, missing agents, seeding unit cases | Real concurrent missing-agent seeding, repeated initialization, edited-record preservation, application-runner activation |
| FR-05, BR-06–08 | All 16 status pairs, missing assignee, CLOSED protection | Persisted lifecycle including reopening and final CLOSED rejection |
| FR-06, BR-09–10 | Comment limits, preservation, version propagation, chronological ties | Real embedded comment writes, UUID/date mapping, persistence/version increments |
| D18, D25, NFR-02–03 | Stale browser and simulated lock conflicts | Real stale repository copies and barrier-controlled HTTP read/save races; losing writes must not overwrite winners |
| R3, NFR-04 | Prior mocked browser checks at 375px | Final mocked full-workflow regression; real frontend/backend/Atlas end-to-end verification still pending |
| R4, NFR-05–06 | Loopback backend, environment URI, feature packages, DTOs and centralized errors | Authorized DB/network configuration, staged-secret review and human assessment/Jira evidence remain human gates |

Correction: the nested exception-handler test controller is profile-restricted so it cannot leak into full application scanning. A metadata-only scan test verifies this without starting the application or creating a Mongo client.

## Safe default and isolated runs

From `backend`, `mvn test` runs isolated tests only. Surefire excludes `BackendApplicationTests` and `*IT`. No dependencies were added. Existing test cases/assertions remain in place.

The separate `mongo-integration` profile selects the seven real Mongo tests plus the existing full application context test, and writes reports to `target/mongo-integration-reports`. Both entry points validate configuration before context refresh. There is no fallback to `MONGODB_URI` or a development database.

**MongoDB execution is not authorized and has not been performed. Do not run the following until the human explicitly approves the exact isolated target.**

Required environment variables, supplied outside Git and command arguments:

- `TICKETFLOW_TEST_DB_AUTHORIZED=true` — set only after explicit human authorization.
- `TICKETFLOW_TEST_DATABASE=ticketflow_it_<32 lowercase hexadecimal characters>` — a newly chosen, empty, isolated database for each run.
- `TICKETFLOW_TEST_DATABASE_CONFIRMATION` — exactly the same database name.
- `TICKETFLOW_TEST_MONGODB_URI` — authorized URI naming that exact database, with `w=majority`; reads must be primary (or default primary). Do not put a real URI into documentation, terminal arguments, screenshots, or reports.

After approval and configuration only: `mvn -Pmongo-integration test`.

The profile supplies an additional execution flag. Missing authorization, absent/mismatched/non-test database names, absent/mismatched URI targets, unacknowledged writes or secondary reads fail closed. URI parser exceptions and URI-bearing object representations are redacted. Driver diagnostics are disabled in the guarded contexts. Unit tests check the guard using synthetic local URI strings without constructing clients or connecting.

The main integration context starts with seeding disabled and requires empty tickets/agents before test writes. Tests then seed concurrently, preserve an edited demo agent, and separately verify opt-in application startup seeding. The full context smoke test also uses the same guarded database with seeding disabled. No test deletes records, drops databases/collections, resets data, or automatically migrates identifiers. All records are retained for human inspection; rerunning requires a fresh explicitly confirmed database. Use credentials restricted to that database and an approved Atlas network configuration.

## Remaining limits

Executed without database access: `mvn -o -ntp test` passed **303 isolated cases** (279 preserved plus 24 coverage/guard cases), with zero failures, errors or skips. All 26 test source files, including the database suite, compiled. Frontend ESLint and production build passed. A mocked browser journey passed creation, list/details, assignment, status progression, comment preservation, CLOSED immutability, shared versions and 375px overflow checks. No database profile or database-dependent context test was executed.

Prepared database tests are compiled, not executed. Atlas authorization/configuration, real optimistic locking, full context startup and actual persistence remain unverified. Mocked browser checks do not establish real Atlas end-to-end behavior. The guard verifies explicit configuration and naming, not administrative ownership of a database; the human must confirm isolation and database-scoped credentials. No public deployment or authentication is introduced.
