# TicketFlow — Phase 2 Design Review and Implementation Handoff

**Reviewed:** 19 November 2026  
**Scope:** Human-approved requirements FR-01–FR-06, approved architecture D1–D25, review corrections R1–R4.  
**Status:** Design approved for implementation; tests and implementation are **not yet performed**.

## Requirements-to-design traceability

| Requirement (Jira) | Classes and storage | API | Acceptance evidence to collect later |
|---|---|---|---|
| FR-01 (`TIC-3`) Create Ticket | `Ticket`, `Priority`, tickets collection | `POST /api/tickets` 201 | Valid create; 400 blank/over-limit/invalid priority |
| FR-02 (`TIC-4`) List Tickets | `TicketService` + `TicketRepository`; agent summaries | `GET /api/tickets` 200 | Newest-first summaries; empty list |
| FR-03 (`TIC-5`) Ticket Details | `Ticket` with embedded `Comment`, optional assigned agent | `GET /api/tickets/{id}` 200 | Correct details and comment order; 404 unknown ticket |
| FR-04 (`TIC-6`) Assign Agent | `Ticket.assignAgent`, `TicketService` validates agent existence; `AgentService` lists agents | `GET /api/agents` 200; `PATCH /api/tickets/{id}/assignment` 200 | Existing agent works; 404 unknown agent; 409 closed/version conflict |
| FR-05 (`TIC-7`) Update Status | `Ticket.changeStatus`, `TicketStatus`, versioned save | `PATCH /api/tickets/{id}/status` 200 | Valid transitions; 409 missing assignee/invalid transition/version |
| FR-06 (`TIC-8`) Add Comments | `Ticket.addComment`, embedded `Comment`, versioned save | `POST /api/tickets/{id}/comments` 201 | Chronological comments; 400 blank/over-limit; 404 missing; 409 closed/version |

## Design findings and resolutions

| Finding | Before review | Human-approved resolution | Remaining consideration |
|---|---|---|---|
| R1 Agent endpoint missing class path | `GET /api/agents` specified but `AgentController`/`AgentService` not depicted | Add both; `AgentController → AgentService → AgentRepository` | Small extra layering and test surface |
| R2 Field limits unspecified | Nonblank checks only | Title 120, description 2000, requester 100, comment author 100, comment 2000 | Changes reject very long input; sync Jira `TIC-3`, `TIC-8` |
| R3 NFRs partly qualitative | Readability/quality described without clear measurements | 375px no horizontal overflow; input validation behavior; automated domain/API tests before Done | Tests must actually be executed later |
| R4 Unauthenticated API exposure | No login by MVP design | Local development and controlled academic demo; no publicly accessible unauthenticated backend | Revisit if public deployment becomes required |

## Cross-document consistency checks

- Comments remain embedded under `Ticket` and **no** third MongoDB collection exists (D8 final A).
- Only two MongoDB collections: `tickets` with `Long version` and `agents` containing preloaded records.
- Agent assignment uses an ID rather than embedding agent documents; service validates existence.
- Priority is chosen at creation and has no edit API.
- Status workflow is OPEN → IN_PROGRESS → RESOLVED → CLOSED, plus RESOLVED → IN_PROGRESS; an assignee is required to enter IN_PROGRESS; CLOSED is immutable.
- Backend generates IDs/timestamps; frontend sends the last received version on mutations; stale updates become HTTP 409.
- Frontend uses Vite proxy during development and displays error/conflict feedback; no authentication or public production deployment is implied.
- API has seven routes total: six under `/api/tickets`, one read-only under `/api/agents`.

## Minimum implementation verification checklist (Phase 3 onward)

1. Verify Spring Boot and React start successfully and Atlas is securely configured with no secrets in commits.
2. Verify DTO/domain validation (whitespace, exact maximum, one character over maximum) with no invalid stored data.
3. Run unit tests for every allowed and disallowed transition, closed-ticket behavior, assignment precondition, and comment validation.
4. Run API/integration tests for success and 400/404/409 outcomes of applicable stories.
5. Create a stale-browser and competing-write scenario to exercise `expectedVersion` and MongoDB `@Version` handling.
6. Manually inspect the UI at 375px width; capture loading, empty, validation, not-found and conflict feedback.
7. Update Jira progress, review code/diffs, record any justified refactoring and real Codex interactions; rerun tests before claiming a story Done.

## Evidence to preserve for final report

- Decisions D1–D25 and the developer's explicit review approvals R1–R4.
- System architecture diagram, class UML, two-collection data model and seven-endpoint API contract.
- This findings table showing **AI review → human-approved changes → updated design**.
- Screenshots of the design diagrams or commits; later attach implementation/test outputs **only after they occur**.

**Review conclusion:** All six approved functional requirements have a design path. The four identified design issues are addressed in the revised artifacts, so Phase 2 is ready to hand off to implementation. This is not a claim of a running system or verified tests.
