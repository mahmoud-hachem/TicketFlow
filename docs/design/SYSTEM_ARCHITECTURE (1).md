# TicketFlow — REST API Contract

**Phase 2, Steps 4–5 | Final approved design baseline | 2026-11-19**  
**Status:** Human decisions D20–D25 and design review corrections R1–R4 approved. The request/response schema and error mappings are the approved implementation contract. No running API or passing tests are claimed.

## Context and constraints

- **Frontend:** React + Vite. During local development, frontend calls use relative `/api` URLs and Vite forwards them to Spring Boot.
- **Backend:** One Spring Boot application organized by feature; controllers accept DTOs, services coordinate, the Ticket domain object protects its state, and Spring Data MongoDB repositories persist tickets/agents.
- **Persistence:** MongoDB Atlas. A ticket contains embedded comments, a single `assignedAgentId` reference, and a Spring Data `@Version Long version` field. Agent records are seeded when missing.
- **Scope:** Approved features US-01–US-06 only. No authentication, authorization, search, pagination, ticket deletion, edit-priority operation, or agent-management UI. Requester and support-agent names do **not** prove identity.
- **Formats:** JSON on successful requests/responses (`application/json`); standardized RFC 9457 Problem Details for errors (`application/problem+json`); timestamps are ISO 8601 UTC strings in the API, backed by Java `Instant`/MongoDB BSON Date.
- **Versioning:** The last returned ticket `version` must be included as `expectedVersion` in each modifying request. Services reject mismatches before mutation and must still rely on MongoDB `@Version` when saving to detect concurrent writes between read and save. Do not automatically retry failures.

## Endpoints

| Jira story | Method | Path | Success | Notes |
|---|---|---|---|---|
| US-01 | `POST` | `/api/tickets` | `201 Created` | Creates OPEN ticket; response contains ticket detail; `Location: /api/tickets/{id}` |
| US-02 | `GET` | `/api/tickets` | `200 OK` | JSON array of ticket summaries, newest `createdAt` first; tie-break by ID; empty array if none |
| US-03 | `GET` | `/api/tickets/{id}` | `200 OK` | Ticket details with comments chronologically ordered |
| US-04 | `PATCH` | `/api/tickets/{id}/assignment` | `200 OK` | Assign/reassign one existing agent; closed tickets rejected |
| US-05 | `PATCH` | `/api/tickets/{id}/status` | `200 OK` | Enforce allowed transitions and assignment prerequisite |
| US-06 | `POST` | `/api/tickets/{id}/comments` | `201 Created` | Embedded comment; closed tickets rejected; returns comment and updated ticket version |
| US-04 (support) | `GET` | `/api/agents` | `200 OK` | `AgentController → AgentService → AgentRepository` returns read-only seeded agents; no CRUD |

## Request DTOs

Field names and types are part of the planned external contract. Domain objects are **not** accepted as request payloads.

| DTO | Fields | Validation |
|---|---|---|
| `CreateTicketRequest` | `title: string`, `description: string`, `requesterName: string`, `priority: "LOW"\|"MEDIUM"\|"HIGH"` | Required, trim surrounding whitespace and reject blank values; `title` ≤120, `description` ≤2000, `requesterName` ≤100 characters after trimming. Status, IDs, timestamps, agent assignment and comments are supplied by backend. |
| `AssignTicketRequest` | `agentId: UUID string`, `expectedVersion: integer` | Both required; UUID format valid; version must be nonnegative and match the last retrieved version. Agent must exist. |
| `ChangeTicketStatusRequest` | `status: "OPEN"\|"IN_PROGRESS"\|"RESOLVED"\|"CLOSED"`, `expectedVersion: integer` | Both required; valid enum; version nonnegative. Only approved transitions allowed. |
| `AddCommentRequest` | `authorName: string`, `content: string`, `expectedVersion: integer` | All required; trim and reject blank text; `authorName` ≤100 and `content` ≤2000 characters; version nonnegative. Ticket must not be CLOSED. |

**Out-of-scope mutations:** There is no general-purpose `PATCH /api/tickets/{id}` endpoint, no `DELETE`, and no priority-editing endpoint. No `requesterId` or logged-in user account is assumed.

### Example: Create a ticket

`POST /api/tickets`

```json
{
  "title": "Laptop cannot connect to Wi-Fi",
  "description": "Connection drops after a few minutes",
  "requesterName": "Example Requester",
  "priority": "MEDIUM"
}
```

`201 Created` with `Location: /api/tickets/b8f6ee9f-bf96-45d2-ac64-e6b1f208301a` and a `TicketDetailResponse`. The initial `version` is persistence-managed and MUST be read from the actual response, not assumed to be any particular number.

### Example: Assign an agent

`PATCH /api/tickets/b8f6ee9f-bf96-45d2-ac64-e6b1f208301a/assignment`

```json
{
  "agentId": "c8539928-82e4-45fb-8a69-e26d35ad39c5",
  "expectedVersion": 2
}
```

`200 OK` with the updated `TicketDetailResponse`, including its **new** version.

### Example: Update status

`PATCH /api/tickets/b8f6ee9f-bf96-45d2-ac64-e6b1f208301a/status`

```json
{
  "status": "IN_PROGRESS",
  "expectedVersion": 3
}
```

`200 OK` with an updated `TicketDetailResponse`. Allowed status transitions are `OPEN → IN_PROGRESS`, `IN_PROGRESS → RESOLVED`, `RESOLVED → CLOSED`, and `RESOLVED → IN_PROGRESS` (reopen). Entering IN_PROGRESS requires an assigned agent; CLOSED is terminal. Repeating the same transition is not defined as an automatic success: it is rejected as invalid.

### Example: Add a comment

`POST /api/tickets/b8f6ee9f-bf96-45d2-ac64-e6b1f208301a/comments`

```json
{
  "authorName": "Example Agent",
  "content": "Investigating the issue",
  "expectedVersion": 4
}
```

`201 Created` with `CommentCreatedResponse`. A comment is part of the ticket document, **not** a third collection. The response includes the **updated ticket version**, ensuring React knows which version to use next.

## Response DTOs

| DTO | Fields | Notes |
|---|---|---|
| `AgentSummaryResponse` | `id`, `name` | No agent email exposed unless a future requirement justifies it |
| `TicketSummaryResponse` | `id`, `title`, `requesterName`, `status`, `priority`, `assignedAgent` (object or null), `createdAt`, `updatedAt`, `version` | Used by GET list; agent resolved by service, not embedded in MongoDB |
| `CommentResponse` | `id`, `authorName`, `content`, `createdAt` | Comments display chronological ascending, with ID as stable tie-breaker |
| `TicketDetailResponse` | All summary fields plus `description`, `comments: CommentResponse[]` | Returned by create, get details, assignment and status change; `comments` never null |
| `CommentCreatedResponse` | `ticketId`, `comment: CommentResponse`, `ticketVersion`, `updatedAt` | Returned by comment creation with HTTP 201 |

Example `TicketDetailResponse` (illustrative):

```json
{
  "id": "b8f6ee9f-bf96-45d2-ac64-e6b1f208301a",
  "title": "Laptop cannot connect to Wi-Fi",
  "description": "Connection drops after a few minutes",
  "requesterName": "Example Requester",
  "priority": "MEDIUM",
  "status": "IN_PROGRESS",
  "assignedAgent": {
    "id": "c8539928-82e4-45fb-8a69-e26d35ad39c5",
    "name": "Example Agent"
  },
  "comments": [
    {
      "id": "7a457e7c-5de5-4c30-bce6-8b2ac1371c0d",
      "authorName": "Example Agent",
      "content": "Investigating the issue",
      "createdAt": "2026-11-19T10:05:00Z"
    }
  ],
  "createdAt": "2026-11-19T10:00:00Z",
  "updatedAt": "2026-11-19T10:05:00Z",
  "version": 5
}
```

Example `CommentCreatedResponse` (illustrative):

```json
{
  "ticketId": "b8f6ee9f-bf96-45d2-ac64-e6b1f208301a",
  "comment": {
    "id": "7a457e7c-5de5-4c30-bce6-8b2ac1371c0d",
    "authorName": "Example Agent",
    "content": "Investigating the issue",
    "createdAt": "2026-11-19T10:05:00Z"
  },
  "ticketVersion": 5,
  "updatedAt": "2026-11-19T10:05:00Z"
}
```

## Validation and error contract

A central Spring `@RestControllerAdvice` produces `ProblemDetail` responses (RFC 9457). At minimum include `type`, `title`, `status`, `detail`, and `instance`; optional stable `errorCode` and safe `fieldErrors` extensions support frontend messages. Never expose stack traces, connection strings, or sensitive data.

| Scenario | HTTP | Suggested `errorCode` |
|---|---|---|
| Missing/blank or over-length required field, invalid enum/UUID/JSON, missing `expectedVersion` | `400 Bad Request` | `VALIDATION_ERROR` |
| Unknown ticket ID | `404 Not Found` | `TICKET_NOT_FOUND` |
| Unknown agent ID supplied to assignment | `404 Not Found` | `AGENT_NOT_FOUND` |
| Invalid status transition or missing assignee when entering IN_PROGRESS | `409 Conflict` | `INVALID_TRANSITION` / `ASSIGNEE_REQUIRED` |
| Attempt to assign, transition, or comment on CLOSED ticket | `409 Conflict` | `TICKET_CLOSED` |
| Submitted `expectedVersion` differs from stored version | `409 Conflict` | `VERSION_CONFLICT` |
| MongoDB `OptimisticLockingFailureException` during save | `409 Conflict` | `VERSION_CONFLICT` |
| Unexpected server error | `500 Internal Server Error` | `INTERNAL_ERROR` (generic detail only) |

Illustrative conflict response:

`HTTP/1.1 409 Conflict`  
`Content-Type: application/problem+json`

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "This ticket changed since you opened it. Reload and try again.",
  "instance": "/api/tickets/b8f6ee9f-bf96-45d2-ac64-e6b1f208301a/status",
  "errorCode": "VERSION_CONFLICT"
}
```

The React client handles `409 VERSION_CONFLICT` by displaying a conflict message and offering a **reload and manual retry**. It must **not automatically retry** assignment/status/comment requests, which could apply unexpected changes after another user's work. For other `409` errors, display the specific business rule message.

## Concurrency sequence for any mutation

1. Client reads a ticket and remembers `version` from response.
2. Client sends the requested operation and `expectedVersion`.
3. Service loads the current ticket, rejects a version mismatch **before** invoking its domain operation, then checks cross-document requirements (e.g. agent exists).
4. Domain object validates its invariants and performs the requested state change.
5. `TicketRepository.save(ticket)` uses Spring Data `@Version` so a competing update occurring after step 3 also causes a conflict with acknowledged write concern.
6. Success returns the new version. Any version mismatch becomes `409 VERSION_CONFLICT`; the user reloads and decides whether to retry.

This protects both **stale browser requests** and **concurrent database writes**. It does not implement login, authorization, or verified comment authorship.

## Traceability and future tests

| Story | Main successful test | Negative/edge tests |
|---|---|---|
| US-01 (`TIC-3`) | POST returns 201, OPEN, generated ID/time | Blank fields or invalid priority → 400 |
| US-02 (`TIC-4`) | GET returns newest-first list | Empty dataset → `[]` |
| US-03 (`TIC-5`) | GET detail includes comments and agent summary | Unknown ticket → 404 |
| US-04 (`TIC-6`) | PATCH assignment returns new version | Unknown agent → 404; closed → 409; stale version → 409 |
| US-05 (`TIC-7`) | PATCH allows approved transitions | Invalid transition or no agent → 409; stale version → 409 |
| US-06 (`TIC-8`) | POST comment returns 201 and updated ticketVersion | Blank comment → 400; closed → 409; stale version → 409 |

## Design implications

- **Backend:** Feature packages `ticket/` and `agent/`, DTOs rather than exposing document classes, centralized error handling, version-aware service operations and repositories.
- **Frontend:** Use `/api/...` relative paths through Vite dev proxy, maintain latest ticket version, update state from returned JSON, and present conflict/error feedback.
- **MongoDB:** Only `tickets` and `agents`; `@Version Long` stays persistence-controlled and cannot be directly changed via API.
- **Delivery:** Development proxy is not a production deployment plan; review hosting/API routing if deployment is required.

## Official implementation references

- Spring Framework, RFC 9457 Problem Details: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Data MongoDB, versioned updates: https://docs.spring.io/spring-data/data-mongo/reference/mongodb/template-crud-operations.html

**Review status:** Reconciled against the six approved requirements in Step 5; see `docs/design/DESIGN_REVIEW.md`. Do not claim passing API tests or deployed endpoints until implementation and testing.

## Approved Step 5 design review constraints (R1–R4)

- **R1:** Agent-list endpoint is served by a dedicated `AgentController` and read-only `AgentService`; its DTO is `AgentSummaryResponse(id, name)`. Ticket assignment still validates agent existence through a repository lookup within the service layer.
- **R2:** Enforce text limits listed in `CreateTicketRequest` and `AddCommentRequest` above using Bean Validation and domain invariants. Over-limit fields produce `400 VALIDATION_ERROR`; no persisted changes. Empty spaces do not count as meaningful text.
- **R3:** Verify a success path and relevant failure paths for each Jira story. At minimum check 400 validation, 404 missing resources, 409 closed/invalid transitions, and 409 version conflicts. In React, verify layout at 375px width and visible form/loading/error/conflict feedback. Tests are planned, **not yet executed**.
- **R4:** Do not expose a publicly accessible unauthenticated write API. During development use the Vite `/api` proxy and local Spring Boot; allow only controlled demo access and restrict Atlas network credentials. No authentication/authorization claim is made.

No change to the seven approved endpoints or the six user stories.
