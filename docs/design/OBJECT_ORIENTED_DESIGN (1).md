# TicketFlow — MongoDB Document Design

**Phase 2 Steps 3–5 · Approved design baseline; R2 validation amended · 2026-11-19**

## Collections

### `tickets` (root document)

| Java field | MongoDB field | Type | Notes |
|---|---|---|---|
| `id` | `_id` | String (UUID) | Unique, backend-generated; map explicitly to string |
| `title` | `title` | String | Required, trimmed nonblank, ≤120 characters |
| `description` | `description` | String | Required, trimmed nonblank, ≤2000 characters |
| `requesterName` | `requesterName` | String | Required, trimmed nonblank, ≤100 characters; unverified identity |
| `priority` | `priority` | String enum | LOW / MEDIUM / HIGH; immutable after creation |
| `status` | `status` | String enum | OPEN / IN_PROGRESS / RESOLVED / CLOSED |
| `assignedAgentId` | `assignedAgentId` | String UUID or null | Cross-record existence checked by service |
| `comments` | `comments` | Array of objects | Embedded; no comments collection |
| `createdAt` | `createdAt` | BSON Date | Backend clock |
| `updatedAt` | `updatedAt` | BSON Date | Backend clock; updated on mutations |
| `version` | `version` | 64-bit integer | Spring Data `@Version` optimistic lock |

**Embedded `Comment` fields:** `id` (UUID String), `authorName` (required trimmed nonblank String, ≤100 characters), `content` (required trimmed nonblank String, ≤2000 characters), `createdAt` (BSON Date). Comment order follows append order for normal sequential writes.

### `agents`

| Java field | MongoDB field | Type | Notes |
|---|---|---|---|
| `id` | `_id` | String (UUID) | Stable seeded UUID |
| `name` | `name` | String | Display name |
| `email` | `email` | String | Sample contact; no authentication |

Sample agents are added idempotently with predetermined stable IDs if missing. The seeder never clears the collection or overwrites existing records.

## Example logical record (illustrative JSON)

The following JSON is a **conceptual** representation: BSON Dates will be stored as date values, not literal ISO strings. The version shown is illustrative; its persisted initial value is managed by Spring Data.

```json
{
  "_id": "b8f6ee9f-bf96-45d2-ac64-e6b1f208301a",
  "title": "Laptop cannot connect to Wi-Fi",
  "description": "Connection drops after a few minutes",
  "requesterName": "Example Requester",
  "priority": "MEDIUM",
  "status": "IN_PROGRESS",
  "assignedAgentId": "c8539928-82e4-45fb-8a69-e26d35ad39c5",
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
  "version": 2
}
```

## State invariants

- New ticket: status OPEN, no assignee, empty comment list, createdAt and updatedAt set by backend.
- Ticket title, description and requesterName nonblank and subject to the approved R2 length limits (120/2000/100); priority must be LOW/MEDIUM/HIGH.
- Valid status transitions: OPEN→IN_PROGRESS→RESOLVED→CLOSED, and RESOLVED→IN_PROGRESS.
- Entering IN_PROGRESS requires assignedAgentId; CLOSED rejects assignments, comments and status changes.
- Agent assignment requires lookup in `agents` before persisting a valid ID.
- Only domain operations change managed ticket fields; priority never changes after creation.
- For embedded comments, `Ticket.addComment(...)` validates and appends; `TicketService` saves the aggregate.

## Concurrency and indexing

- `Ticket.version` uses Spring Data MongoDB optimistic locking (`@Version Long`). Each update matches the previously read version; stale writes are rejected. Set/retain acknowledged MongoDB write concern and map conflict to HTTP 409 in Step 4.
- Only default `_id` indexes on `tickets` and `agents`. No creation-date, priority or status indexes for MVP. Sorting in descending createdAt order is still required by US-02; performance must not be assumed for larger data sets.
- MongoDB has a 16 MiB maximum BSON document size, a known trade-off of embedded comments; this is acceptable for the deliberately limited MVP.

## Engineering rationale and evidence

Human-approved decisions: D15 Java UUID strings, D16 DTO validation plus domain invariant checks, D17 `_id` indexes only, D18 optimistic lock with version, D19 idempotent stable agent seed records. Details and alternatives appear in `docs/PHASE2_ENGINEERING_DECISIONS.md`.

Reference documentation:
- https://docs.spring.io/spring-data/mongodb/reference/mongodb/template-crud-operations.html
- https://docs.spring.io/spring-data/data-mongo/reference/mongodb/mapping/mapping.html
- https://www.mongodb.com/docs/manual/reference/limits/

**Status:** Design only. Real DB mapping, seeding behavior, concurrency behavior, and tests must be implemented and verified later.

## Step 5 design review consequences

- **R1:** `agents` remains read-only from the API: `AgentController → AgentService → AgentRepository`; there is no agent creation/edit/delete endpoint.
- **R2:** Maximum input lengths also apply within the domain, even when operations originate outside the HTTP API; MongoDB is not relied upon as the sole validator.
- **R3:** Planned tests check that rejected values never persist and that stale version writes return 409 without losing data.
- **R4:** Atlas credentials remain outside Git and access is restricted to local development/controlled demonstrations; do not open a public unauthenticated write API.
