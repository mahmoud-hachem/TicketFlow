# TicketFlow — Human–AI Development Record

This record summarizes repository evidence and the human-supplied final results as of 26 November 2026. It is not a transcript of every interaction, a claim of teammate approval, a Jira status update or a measured effort log. The human owns scope, engineering decisions, credentials and acceptance; AI assistance does not establish approval or correctness by itself.

## Human decisions and alternatives

| Evidence | Human decision | Alternative and trade-off |
|---|---|---|
| [ADR-001](decisions/ADR-001.md) | Six-feature MVP; React, Spring Boot and a layered modular monolith | Microservices add deployment/network complexity; a technical-layer monolith scatters feature behavior. Feature boundaries still require discipline. |
| ADR-001 | MongoDB Atlas with Spring Data MongoDB | H2 reduces network setup; Atlas supports hosted collaboration but requires credentials, network access and isolated test targets. JPA is not used. |
| [D1–D7, D14](PHASE2_ENGINEERING_DECISIONS.md) | Feature packages, DTOs, domain-owned invariants, coordinated services, repositories and centralized errors | Data-only documents and service-only rules reduce domain code but weaken encapsulation and direct rule testing. DTOs add mapping work. |
| D8 amendment, D9–D13 | Embed comments; reference agents by ID; keep requester as a name; seed agents; fix priority at creation | The human initially chose a separate comments collection, then explicitly revised D8 to embedding. Embedding simplifies reads/atomic saves but grows ticket documents; no requester identity or agent-admin UI is implied. |
| D15–D19 | UUID strings, DTO/domain validation, default indexes only, optimistic locking, insert-only missing-agent seeding | ObjectIds and extra indexes were considered. String IDs cost space; unindexed lists and embedded comments limit scale. Seeding must preserve edited records and concurrent initialization. |
| D20–D25 | Resource routes, specialized mutations, Problem Details, fresh response versions and manual conflict recovery | Generic PATCH and automatic retries reduce UI/API code but obscure allowed operations and risk stale or duplicated work. |
| R1–R4 | Add agent service/controller, bounded text sizes, measurable verification and controlled local demo | More classes/tests and validation restrictions; public deployment remains inappropriate for an unauthenticated API. |
| IT-01, owner instruction | Accept exact `ticketflow_test` with database-scoped `readWrite` credentials | Requiring only generated names blocked the dedicated authorized database. A narrow exact-name exception preserves confirmation, URI matching, majority writes, primary reads and empty-data checks; unauthorized names remain rejected. |

Design approvals in the existing log belong to the human developer. This document records no additional teammate sign-off. Provisional sprint estimates of 18 and 19 person-hours were planning inputs, not actual elapsed effort.

## AI contributions and review boundaries

ChatGPT/Codex supplied alternatives, architecture and API rationale, requirements traceability, implementation assistance, test preparation, focused quality refactoring and documentation corrections. The repository's [quality record](CODE_QUALITY.md) describes the shared frontend mutation hook, private backend version/time helpers and preservation of regression assertions. Human decisions constrained these changes; the documentation does not attribute every line or test to a particular person without evidence.

Codex implemented the IT-01 guard exception and expanded guard assertions while preserving functional behavior. It reported 311 passing isolated cases. During authentication diagnosis, it inspected reports/configuration without displaying credentials, distinguished client-side password preparation from server bad-auth reports, and stopped when no environment credentials were available. It did not obtain an authenticated probe in that session. The later successful real integration and browser results were supplied as human-verified evidence.

## Corrections and lessons from failed approaches

- **Versioning needs both boundaries.** A persistence `@Version` field alone does not detect a browser submitting an outdated intent after the server has already reloaded current data. Explicit `expectedVersion` plus the repository lock protects both stale requests and competing saves.
- **Design review changed implementation clarity.** R1 added the missing agent service/controller path; R2 set text limits; R3 defined measurable checks; R4 restricted exposure. D8's human amendment superseded the earlier separate-comments choice.
- **A green context is insufficient.** Earlier context startup passed while the first authenticated read failed. Human-reported server error 8000 and both SCRAM attempts were distinct from the inspected 22:33 SCRAM-SHA-256 failure in `SaslPrep.saslPrepStored`, which rejected a prohibited password character. URI encoding, control-character contamination and credential validity were possibilities, not proven causes. The exact change leading to the 22:39 success was not supplied; no password reset or specific correction is invented.
- **Environment failures are not product failures.** Offline Maven initially hit a cache-lock restriction; process-local locking avoided it. Mockito self-attachment then hit a Windows pipe restriction; a startup agent enabled the isolated run. JaCoCo was disabled for that IT-01 retry, so it supplies no new coverage percentage. The quality record separately documents the PMD rule correction and mocked-browser harness/process corrections.
- **Preserve unique evidence.** Misnamed Phase 2 files were renamed by internal content with hashes preserved. A byte-identical design-review copy was consolidated, and a current architecture overview was added. Historical design statements are explicitly separated from final results.
- **Metrics guide, rather than prove, quality.** Focused helpers reduced duplication without introducing an unrelated state-management rewrite. A reducer, broader App decomposition, stricter quality thresholds, broader analyzers and a project-managed browser dependency remain deferred; none is implied by lower complexity.

## Verification evidence and its limits

| Stage/source | Result and scope |
|---|---|
| Quality refactoring, before IT-01 | 303 isolated tests passed. Java line coverage 97.87%, branch coverage 93.29%; Java complexity 211 → 202; frontend complexity 316 → 282. These are pre-IT-01 measurements. |
| IT-01, reported by Codex | 311 isolated cases passed; zero failures/errors/skips, including 21 guard cases. Existing assertions retained. No integration or new coverage claim from that run. |
| Real MongoDB, human-verified | 7 integration tests + 1 application context test passed, zero failures/errors/skips; BUILD SUCCESS, 26 November 2026 at 22:39, 35.114 seconds. |
| Real browser, human-verified | Creation, list, details, assignment, reassignment, status progression and CLOSED protection verified. Manual comment demonstration intentionally omitted; comments covered by automated and real integration tests. |
| Earlier quality verification | Frontend lint/build and 16 mocked browser scenario groups passed. Mocked 375px checks do not establish a real mobile/browser matrix. |

The final results supersede the earlier authentication blocker, but do not prove every environment or workload. Test data is retained; future runs must not reset it. Credentials remain outside Git and command arguments, supplied through the launching process environment. Detailed evidence, secure configuration and troubleshooting chronology are in [TIC10_VERIFICATION.md](TIC10_VERIFICATION.md).

## Remaining limitations and handoff

The application has no authentication/authorization, verified identities or public deployment plan. Atlas availability/network configuration remain prerequisites. No load testing, production-scale indexing, pagination or bounded lifetime comment storage is established. Coverage measures executed paths; complexity algorithms differ across tools and their totals should not be compared directly. App remains large with 15 state hooks. Manual comment demonstration was deliberately omitted, not a failed feature.

Current Jira statuses, acceptance-criteria synchronization, academic assessment and teammate approvals require direct human evidence. Documentation finalization changed no application logic and ran no database operations or integration tests. Pending source/test guard edits belong to IT-01. No commit or push was performed.

## Documentation consistency review

Current results in README, TIC-10 verification and the quality record distinguish the 303-case pre-IT-01 run, the 311-case Codex-reported run, the human-verified 8-test real run and the deliberately narrower manual browser demonstration. Quality percentages and complexity remain labeled pre-IT-01. Earlier authentication failures are historical evidence, not current blockers. Local Markdown links were checked after the filename corrections. Historical design content and all unique content were retained.

## Pending Git review

The unrelated `.env.example` edit changes the `MONGODB_URI` username, password, host and database components while leaving the query unchanged. The original example used recognizable username/password placeholders; the working copy contains non-placeholder credential fields. Its value was not displayed, used or copied into documentation, and the file was left untouched. Treat it as a potential credential exposure requiring owner review/redaction before any commit or sharing; validity was not tested. If those are active credentials and have already been shared, their owner must assess rotation. This is a repository hygiene blocker, not a failed integration result.

The pending-file scan found no other credential/token patterns and no pending generated report, build or dependency files. Pattern scanning cannot prove absence of every secret. Generated artifacts remain ignored and were not added. Existing guard/test edits from IT-01 were preserved; this finalization added only documentation edits, moves and duplicate consolidation.
