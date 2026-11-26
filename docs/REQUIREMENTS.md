# TicketFlow — MVP Requirements

**Planning baseline:** 19 November 2026  
**Status:** Approved MVP baseline; R2–R4 design-review amendments approved 19 November 2026. Changes to Jira acceptance criteria should be synchronized before implementation.

## 1. Scope

TicketFlow allows a requester to submit a support issue and lets support agents manage that ticket. The MVP has six features only. Login, email, attachments, filtering/search and analytics are explicitly excluded.

The absence of authentication means users can enter names, but the software cannot verify real identities or enforce role-based permissions.

## 2. Functional requirements and Jira traceability

| ID | Requirement | Jira |
| --- | --- | --- |
| FR-01 | Create a ticket with title, description, requester name and priority | TIC-3 |
| FR-02 | Display tickets with status, priority and assignee (if any) | TIC-4 |
| FR-03 | Display full ticket details and comments | TIC-5 |
| FR-04 | Assign or reassign one existing agent to a ticket | TIC-6 |
| FR-05 | Update ticket status using the permitted transitions | TIC-7 |
| FR-06 | Add a comment to an existing ticket | TIC-8 |

## 3. Business rules

| ID | Rule |
| --- | --- |
| BR-01 | A new ticket always starts as `OPEN`. |
| BR-02 | Priorities are `LOW`, `MEDIUM`, or `HIGH`. |
| BR-03 | Title, description and requester name must not be blank; after trimming, maximum lengths are 120, 2000 and 100 characters respectively (R2). |
| BR-04 | One ticket has no more than one assigned agent at a time; reassignment replaces the existing assignment. |
| BR-05 | An agent must exist before assignment; nonexistent agents are rejected. |
| BR-06 | A ticket must be assigned before it can move to `IN_PROGRESS`. |
| BR-07 | Allowed status transitions are `OPEN → IN_PROGRESS`, `IN_PROGRESS → RESOLVED`, `RESOLVED → CLOSED`, and `RESOLVED → IN_PROGRESS`. Other direct transitions are rejected. |
| BR-08 | A `CLOSED` ticket is final: no assignment changes, status changes or new comments. |
| BR-09 | A ticket may have multiple embedded comments. Comment author and content must not be blank and are limited to 100 and 2000 characters respectively (R2). |
| BR-10 | Creation timestamps are assigned by the backend. Comments display chronologically. |

## 4. Acceptance criteria (minimum)

### TIC-3 — Create Support Ticket
- A valid title, description, requester and priority create a persisted ticket with an ID and `OPEN` status.
- Missing/blank required fields, over-limit text or invalid priority return `400 Bad Request` with useful validation details; no ticket is created.

### TIC-4 — View All Tickets
- The list displays every existing ticket with title, status, priority, requester and assignment (or *Unassigned*).
- The list shows newest tickets first, a helpful empty state when none exist, and an error message if retrieval fails.

### TIC-5 — View Ticket Details
- Selecting an existing ticket shows title, description, requester, status, priority, dates, agent (if any) and comments in chronological order.
- A nonexistent ticket results in a not-found response/message.

### TIC-6 — Assign Support Agent
- Assigning or reassigning to an existing agent persists the new assignment and shows it in the UI.
- Nonexistent agents are rejected, and closed tickets cannot be assigned or reassigned.

### TIC-7 — Update Ticket Status
- Valid transitions are persisted and reflected in the UI.
- Moving `OPEN` to `IN_PROGRESS` fails without an assigned agent.
- Invalid transitions and any status changes to a `CLOSED` ticket are rejected without updating the stored status.

### TIC-8 — Add Comments to Ticket
- A comment with a nonblank author and message is saved with a timestamp and shown chronologically.
- Blank or over-limit comment inputs return 400; nonexistent tickets return 404; comments on closed tickets return 409. No invalid comment is saved.

## 5. Initial domain model

- **Ticket:** id, title, description, requesterName, priority, status, assignedAgentId (optional), createdAt, updatedAt, comments.
- **Agent:** id, name, email. Initial agents can be seeded as demo records.
- **Comment:** id, authorName, content, createdAt. Comments are embedded in Ticket documents.
- **Enums:** TicketStatus (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`) and Priority (`LOW`, `MEDIUM`, `HIGH`).

## 6. Non-functional requirements (testable targets)

The targets below are **design acceptance criteria**, not claims of completed testing.

| ID | Requirement | Verification criterion |
|---|---|---|
| NFR-01 | Maintainability | Backend code uses feature-oriented `ticket/` and `agent/` packages, DTOs at the API boundary, domain-owned ticket rules, service orchestration and repositories. Inspect package dependencies during code review. |
| NFR-02 | Data integrity and validation | Blank and over-limit inputs are rejected with HTTP 400 and do not persist changes. Closed-ticket operations, invalid transitions and stale versions yield HTTP 409 without lost updates. |
| NFR-03 | Automated testing | All ticket transition rules have automated unit tests; each of the six stories has at least one API/integration success case and relevant failure cases. Relevant tests pass before a story is marked Done. |
| NFR-04 | Responsive usability | Ticket creation, list and details screens are usable at a viewport width of **375 CSS pixels** without horizontal page overflow; loading, empty, validation and conflict states have readable feedback. Verify manually in browser responsive mode. |
| NFR-05 | Secrets and demo exposure | MongoDB credentials never appear in committed files; inspect staged changes and `.gitignore`. The unauthenticated API remains local or accessible only in a controlled academic demo and is not publicly deployed. |
| NFR-06 | Traceability | Jira issue references, meaningful Git commits, reviewed AI interactions and test evidence link requirements to implementation decisions and outcomes. |

## 7. Approved design constraints (Phase 2)

- Client-server contract: `/api/tickets` and `/api/agents` REST endpoints, separate request/response DTOs, standardized Problem Details errors.
- Backend model: `Ticket` domain aggregate containing `Comment` objects; an optional `assignedAgentId` string references seeded Agent records in `agents`.
- Concurrency: versioned Ticket documents; mutating requests contain `expectedVersion` and stale requests are rejected with HTTP 409.
- Field maximums after trimming: `title <= 120`, `description <= 2000`, `requesterName <= 100`, `comment.authorName <= 100`, `comment.content <= 2000` characters. Text must also be nonblank.
- No authentication or authorization is implemented; display names are **not verified identities**.

See the [API contract](design/REST_API_CONTRACT.md), [design review](design/DESIGN_REVIEW.md) and [final verification evidence](TIC10_VERIFICATION.md). The requirement to synchronize Jira acceptance criteria for `TIC-3` (length limits), `TIC-8` (comment length limits) and relevant quality tasks remains a traceability obligation; this repository finalization does not assert that Jira updates occurred.
