# TIC-10 final verification and authentication history

## Final verified outcome

The human developer verified **7 real MongoDB integration tests plus 1 application context test passed**, with **zero failures, errors or skips**, **BUILD SUCCESS**, on **26 November 2026 at 22:39**, duration **35.114 seconds**. This is the supplied final execution evidence; documentation finalization did not access MongoDB or rerun the suite.

The real browser demonstration verified ticket creation, list, details, assignment, reassignment, status progression and CLOSED status protection. Manual comment demonstration was intentionally omitted; comments were covered by automated tests and the successful real integration suite. No broader manual browser coverage is inferred.

The isolated results remain separate: **303 passed after quality refactoring, before IT-01**; **311 passed after IT-01, reported by Codex**. The quality baseline was measured **before IT-01**: Java line coverage **97.87%**, branch coverage **93.29%**, Java complexity **211 → 202**, frontend complexity **316 → 282**. These measurements were not recomputed from the real integration run.

## Review and coverage

The reviewed baseline is [requirements](REQUIREMENTS.md), [ADR-001](decisions/ADR-001.md), D1–D25, R1–R4 and I6-01–I6-12. Phase 2 files now use their internal content names: [API contract](design/REST_API_CONTRACT.md), [UML](design/OBJECT_ORIENTED_DESIGN.md), [data model](design/MONGODB_DATA_MODEL.md) and [design review](design/DESIGN_REVIEW.md). The byte-identical duplicate review was consolidated; all unique design content was preserved. A current [architecture overview](design/SYSTEM_ARCHITECTURE.md) links those artifacts.

| Requirements | Isolated evidence | Real integration coverage, passed in the final run |
|---|---|---|
| FR-01, BR-01–03, R2 | Ticket create/service/controller validation | All priorities, exact trimmed maximums and every blank/over-limit field; real create/read/BSON dates/string IDs/version mapping |
| FR-02–03, BR-10 | Empty lists, service order preservation, details, assignee/comment mapping, 404 | Actual database date/ID sorting, persistence across fresh repository reads, embedded comment ordering |
| FR-04, BR-04–05, D19 | Assignment/reassignment/same-agent, missing agents, seeding unit cases | Real concurrent missing-agent seeding, repeated initialization, edited-record preservation, application-runner activation |
| FR-05, BR-06–08 | All 16 status pairs, missing assignee, CLOSED protection | Persisted lifecycle including reopening and final CLOSED rejection |
| FR-06, BR-09–10 | Comment limits, preservation, version propagation, chronological ties | Real embedded comment writes, UUID/date mapping, persistence/version increments |
| D18, D25, NFR-02–03 | Stale browser and simulated lock conflicts | Real stale repository copies and barrier-controlled HTTP read/save races; losing writes must not overwrite winners |
| R3, NFR-04 | Mocked workflow and 375px checks | Real browser demonstration verified the workflow listed above; manual comments and real 375px checks are not claimed |
| R4, NFR-05–06 | Loopback backend, environment URI, feature packages, DTOs and centralized errors | Owner-authorized integration completed; assessment, Jira status and teammate approvals are not inferred |

Correction: the nested exception-handler test controller is profile-restricted so it cannot leak into full application scanning. A metadata-only scan test verifies this without starting the application or creating a Mongo client.

## Safe default and isolated runs

From `backend`, `mvn test` runs isolated tests only. Surefire excludes `BackendApplicationTests` and `*IT`. No dependencies were added. Existing test cases/assertions remain in place.

The separate `mongo-integration` profile selects the seven real Mongo tests plus the existing full application context test, and writes reports to `target/mongo-integration-reports`. Both entry points validate configuration before context refresh. There is no fallback to `MONGODB_URI` or a development database.

**IT-01:** The project owner authorized the dedicated database `ticketflow_test` with database-scoped `readWrite` credentials. The guard accepts this exact name in addition to the existing generated isolated names. `ticketflow_dev` and other names outside those rules remain rejected. The human-verified final run succeeded; this documentation task performed no connection or database execution.

Required environment variables, supplied outside Git and command arguments:

- `TICKETFLOW_TEST_DB_AUTHORIZED=true` — set only after explicit human authorization.
- `TICKETFLOW_TEST_DATABASE=ticketflow_test` — the exact owner-authorized dedicated database; tickets and agents must be empty before test writes. The existing `ticketflow_it_<32 lowercase hexadecimal characters>` format remains supported with separate explicit target authorization.
- `TICKETFLOW_TEST_DATABASE_CONFIRMATION` — exactly the same database name.
- `TICKETFLOW_TEST_MONGODB_URI` — authorized URI naming that exact database, with `w=majority`; reads must be primary (or default primary). Do not put a real URI into documentation, terminal arguments, screenshots, or reports.

### Owner-run PowerShell commands

For a future separately authorized run with an empty target, use a private PowerShell session. Supply the complete URI at the masked prompt, using database-scoped credentials, database path `/ticketflow_test`, and query options `authSource=admin`, `w=majority` and `readPreference=primary`. Percent-encode username/password components once, not the whole URI. Do not paste the URI into a command, tracked file, transcript, screenshot or report. The URI is passed to Maven through the process environment and cleared afterwards; it necessarily exists in process memory during execution. These commands are reference instructions, not a request to rerun the completed suite or reset its retained records.

```powershell
Set-Location 'C:\Users\User\Desktop\TicketFlow\backend'
$mongoUriSecret = Read-Host 'Authorized MongoDB URI for ticketflow_test' -AsSecureString
$mongoUriPointer = [IntPtr]::Zero
try {
    $mongoUriPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($mongoUriSecret)
    $env:TICKETFLOW_TEST_MONGODB_URI = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($mongoUriPointer)
    $env:TICKETFLOW_TEST_DB_AUTHORIZED = 'true'
    $env:TICKETFLOW_TEST_DATABASE = 'ticketflow_test'
    $env:TICKETFLOW_TEST_DATABASE_CONFIRMATION = 'ticketflow_test'
    & mvn -ntp '-Pmongo-integration' test
    if ($LASTEXITCODE -ne 0) { throw 'TIC-10 integration suite failed; inspect the test reports.' }
}
finally {
    if ($mongoUriPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($mongoUriPointer)
    }
    $mongoUriSecret.Dispose()
    Remove-Item Env:TICKETFLOW_TEST_MONGODB_URI, Env:TICKETFLOW_TEST_DB_AUTHORIZED, Env:TICKETFLOW_TEST_DATABASE, Env:TICKETFLOW_TEST_DATABASE_CONFIRMATION -ErrorAction SilentlyContinue
}
```

These commands are provided for an authorized operator; they were not run during documentation finalization. They are not represented as the exact command transcript of the human-verified final run.

The profile supplies an additional execution flag. Missing authorization, absent/mismatched/non-test database names, absent/mismatched URI targets, unacknowledged writes or secondary reads fail closed. URI parser exceptions and URI-bearing object representations are redacted. Driver diagnostics are disabled in the guarded contexts. Unit tests check the guard using synthetic local URI strings without constructing clients or connecting.

The main integration context starts with seeding disabled and requires empty tickets/agents before test writes. Tests then seed concurrently, preserve an edited demo agent, and separately verify opt-in application startup seeding. The full context smoke test also uses the same guarded database with seeding disabled. No test deletes records, drops databases/collections, resets data, or automatically migrates identifiers. All records are retained for human inspection. If `ticketflow_test` already contains tickets or agents, stop: do not delete, drop or reset it to rerun. A subsequent run needs a fresh separately authorized target. Use credentials restricted to that database and an approved Atlas network configuration.

## Isolated verification history

### IT-01 isolated regression

On 26 November 2026, **311 isolated cases passed**, with zero failures, errors or skips, including 21 guard cases. All existing regression assertions were retained; guard coverage now checks both naming paths, the exact dedicated target, authorization, confirmation, URI matching, majority writes, primary reads, redaction and unauthorized-name rejection. All 26 test source files compiled. `git diff --check` and syntax parsing of the documented PowerShell block passed; the block itself was not executed.

The default offline attempt hit a sandbox cache-lock restriction; a retry then stalled on Mockito self-attachment to a Windows pipe and was stopped. The successful isolated run used process-local Maven locking and the cached Mockito startup agent. JaCoCo was disabled for this run; no new coverage claim is made. Exact successful command, from `backend`:

```powershell
mvn -o -ntp '-Daether.syncContext.named.factory=rwlock-local' '-Djacoco.skip=true' '-DargLine=-javaagent:C:\Users\User\.m2\repository\org\mockito\mockito-core\5.24.0\mockito-core-5.24.0.jar' test
```

### Earlier verification evidence

Before IT-01, `mvn -o -ntp test` passed **303 isolated cases** (279 preserved plus 24 coverage/guard cases), with zero failures, errors or skips. All 26 test source files, including the database suite, compiled. Frontend ESLint and production build passed. A mocked browser journey passed creation, list/details, assignment, status progression, comment preservation, CLOSED immutability, shared versions and 375px overflow checks. No database profile or database-dependent context test was executed in that earlier isolated phase.

The final real suite subsequently passed and supplies persistence, full-context and competing-write evidence for its tested cases. Mocked browser checks alone would not establish Atlas behavior. The guard verifies explicit configuration and naming, not administrative ownership or database permissions. No public deployment, load/performance guarantee or application authentication is introduced. The real browser demonstration covers only the actions listed in the final outcome.

## Authentication diagnosis — 26 November 2026

### Earlier failed attempt at 22:33

Reports inspected during the earlier diagnosis, dated 22:33, showed the application context smoke test passing (1 test, no failures/errors), followed by one integration initialization error at `requireFreshConfirmedDatabaseBeforeTestWrites`, before any integration test writes. These reports predate the successful 22:39 result. Context startup alone does not prove authentication because creating a MongoDB client need not perform an authenticated operation.

That failed attempt's exception chain was `UncategorizedMongoDbException` → `MongoSecurityException` → `IllegalArgumentException` in `SaslPrep.saslPrepStored`, with a prohibited-character diagnostic during SCRAM-SHA-256 password preparation. That report did **not** contain server error 8000; earlier bad-auth attempts and failures with both SCRAM mechanisms were human-reported. The decoded password contained a character rejected by SASL preparation. The evidence did not establish whether it came from URI construction, pasted control characters or the original credential. The exact credential/configuration correction leading to success was not supplied, so no reset or specific fix is claimed. Never remove characters, guess passwords or bypass authentication to resolve such failures.

### Configuration and compatibility checks

The reports identify authentication source `admin`, mechanism SCRAM-SHA-256, Java 24.0.2, and matching Java sync/core/BSON driver versions 5.13.0. No mixed driver versions or compatibility defect was identified; MongoDB's [compatibility table](https://www.mongodb.com/docs/drivers/compatibility/?driver-language=java&java-driver-framework=java-sync) supports Java 21 and later JVM versions by backwards compatibility. The actual server version was not independently queried.

`IsolatedMongoTestInitializer` puts the guarded test URI and database first in Spring's property sources, overriding the application's `MONGODB_URI` placeholder. It disables seeding and driver logs. The exact database name, authorization, confirmation, majority-write and primary-read checks remain intact. No confirmed Spring, guard or application-code defect warrants a behavior change.

### Diagnostic-session limits and future secure configuration

During that diagnostic session, no MongoDB connection variables were available at process, user or machine scope. Credentials were not recovered from files, reports or shell history. No new connection, authentication attempt or integration run was performed by that diagnosis. This was a session-specific blocker, superseded by the human-verified successful final run. The successful suite passed its empty tickets/agents precondition before writing; the current database must not be assumed empty because tests retain records. No tests or database operations were rerun during documentation finalization.

In the same private PowerShell session that will launch Maven, use the masked URI-entry and cleanup commands above. Use the known original database username/password, percent-encode each component **once**, and never encode the whole URI or re-encode an already encoded password. Re-enter from the trusted credential source if copy/paste may have introduced control characters; do not trim or alter an intended password. Set the database path to `ticketflow_test` and explicitly include `authSource=admin`, `w=majority`, and `readPreference=primary`. Atlas authenticates SCRAM database users against `admin` even when their data permissions cover only `ticketflow_test`; this does not grant access to other databases. See [Atlas connection guidance](https://www.mongodb.com/docs/atlas/driver-connection/) and [credential-format troubleshooting](https://www.mongodb.com/docs/drivers/java/sync/current/connection/connection-troubleshooting/).

For future troubleshooting, use only authorized environment credentials and an authenticated read without displaying document contents; do not use `ping` or context startup as authentication evidence. On rejection, stop without password guessing or bypass attempts. A future integration run requires a separately authorized empty target; never delete, drop or reset retained data. A rejected known credential requires owner verification rather than an inferred application-code change.
