# TicketFlow — Phase 2 Engineering Decision Log

**Final status:** Steps 1–5 approved for the Phase 2 design baseline on 19 November 2026. No implementation or testing is claimed.


## Step 1: System Architecture

**Date:** 2026-11-19  
**Decision status:** Approved by the human developer  
**Stage:** Analysis and Design; no application code implemented in this step

### Context

TicketFlow is a six-feature support ticket management mini-project. The agreed stack is React with Vite, Java 21 and Spring Boot, and MongoDB Atlas with Spring Data MongoDB. The backend uses a layered modular monolith, exposed through a JSON REST API. Authentication is outside the MVP. The human developer approved the following implementation-level architecture decisions after considering AI-proposed alternatives and rationale.

| ID | Question | Approved choice | Alternative considered | Rationale | Trade-off / consequence |
|---|---|---|---|---|---|
| D1 | Backend organization | **Feature-oriented packages** (`ticket/`, `agent/`) | Technical-layer packages (`controllers/`, `services/`, `repositories/`) | Keeps related behavior in one feature area and minimizes cross-feature coupling. | Some duplication may occur between modules; package boundaries and dependency direction need discipline. Avoid unnecessary extra subpackages for this mini-project. |
| D2 | Ownership of business rules | **Domain objects enforce invariants; services coordinate operations** | Business rules exclusively in service classes | Makes ticket state transitions and invariants directly unit-testable and supports encapsulation. | Domain methods require deliberate design. Validation involving other records (e.g., an agent exists) belongs in services, not in a domain object. |
| D3 | REST API data contract | **Request/response DTOs** | Return MongoDB document classes directly | Separates external API contracts from persistence objects and controls input/output validation. | Requires mapping code and DTO maintenance; keep DTOs small and task-specific. |
| D4 | MongoDB access | **Spring Data MongoDB repositories** | `MongoTemplate` for routine CRUD | Fits straightforward ticket/agent CRUD and reduces persistence boilerplate. | Less flexible for sophisticated queries; use a custom query or `MongoTemplate` only if a concrete requirement needs it. |
| D5 | API error handling | **Centralized exception handling** | Individual controller-level try/catch blocks | Produces consistent status codes and error responses without repetition. | Need sensible exception types and careful mapping; avoid leaking stack traces or sensitive data. |
| D6 | Development-time React/backend integration | **Vite development proxy for API requests** | Browser calls to separate backend origin with CORS | Simplifies local development and routes relative `/api` requests to Spring Boot. | Applies only to the Vite development server. Production requires a separately defined frontend/API routing and CORS/deployment plan if the origins differ. |

### Approved architecture and responsibilities

- **React + Vite frontend:** user interface, forms, loading/error feedback; sends JSON requests to relative `/api/...` paths during development.
- **Vite development proxy:** forwards `/api` requests to the local Spring Boot application. This is a development convenience, not a production routing strategy.
- **Spring Boot controllers:** HTTP endpoints, request DTO validation, mapping HTTP input/output. Controllers do not own business rules.
- **Ticket domain model:** ticket status transition rules, closed-ticket immutability, and invariants that depend only on ticket state.
- **Application services:** coordinate use cases and interactions between tickets, agents and repositories; enforce cross-record rules such as rejecting unknown agent IDs.
- **Spring Data MongoDB repositories:** persist tickets and agents. MongoDB Atlas remains the approved hosted database.
- **Centralized exception handler:** produces consistent HTTP errors for invalid input, missing resources and business rule violations.

### Implementation constraints

1. Retain the approved six-feature MVP; do not introduce authentication or microservices.
2. Store secrets outside Git and keep a safe environment configuration example.
3. Treat 'requester' and 'agent' as conceptual roles only; without authentication, there is no identity or permission enforcement.
4. Confirm domain class structure and relationships in Phase 2 Step 2 before implementation.
5. Confirm collection schema in Step 3 and API endpoint contracts in Step 4.
6. Verify the architecture through tests and code review during implementation; approval here does not imply working code or completed tests.

### Evidence of human supervision

The AI advisor presented alternatives and recommended choices A for decisions D1–D6. The human developer explicitly confirmed all six choices on **2026-11-19**. These decisions form the approved baseline for the next analysis/design steps. If implementation reveals a problem, revise this record and explain the reason rather than silently changing architecture.

## Step 2: Object-Oriented Design

**Date:** 2026-11-19  
**Decision status:** Approved by the human developer  
**Stage:** Analysis and Design; no application code implemented in this step

The human developer initially selected D8=B (separate comments collection), then **explicitly revised D8 to A (embed comments)**. The latest approval supersedes the earlier one. All eight final choices match the AI advisor's recommended options. The amendment is preserved here so the engineering decision history is accurate.

| ID | Approved design | Alternative evaluated | Rationale | Trade-off / consequence |
|---|---|---|---|---|
| D7 | `Ticket` owns and protects its own state | Data-only ticket mutated by services | Encapsulates ticket invariants and makes direct domain tests possible | Requires deliberate domain methods; avoid unrestricted state setters |
| D8 | **Embed `Comment` objects in `Ticket`** | Separate MongoDB comments collection | Small MVP; comments are normally read with the ticket; simple single-document persistence | Ticket documents grow with comments; independent comment querying/pagination is less convenient |
| D9 | Store `assignedAgentId: String` on `Ticket` | Embed full `Agent` object | Avoids copying agent details; supports reassignment | Agent identity/existence is checked by the service; display name requires lookup |
| D10 | Store `requesterName: String` on `Ticket` | Create `Requester` domain object and collection | No requester profile or login in MVP | Names do not establish verified identity |
| D11 | Seed a small set of `Agent` records | Build agent administration UI | Keeps implementation within six-story scope | No UI to add/remove agents in MVP |
| D12 | Fix priority at ticket creation | Allow priority editing later | Matches approved scope, reduces operations and invariants | Mistakes in initial priority cannot be corrected without a future feature |
| D13 | Backend assigns string identifiers and timestamps | Client supplies identifiers/timestamps | Single source of truth; easier validation and audit | Use an injectable clock and ID generator in services for deterministic testing |
| D14 | Explicit `Ticket.assignAgent`, `Ticket.changeStatus`, and `Ticket.addComment` operations | Public setters for mutable state | Makes state changes intentional and testable | Slightly more domain code |

### Step 2 responsibilities and invariants

- **Ticket (aggregate root):** stores title, description, requester name, status, priority, optional assigned agent ID, embedded comments, and timestamps; enforces valid status changes, closed-ticket immutability, and nonblank local invariants. No unrestricted setters for controlled fields.
- **Comment (embedded value object):** `id`, `authorName`, `content`, `createdAt`; created through `Ticket.addComment()` and persisted inside the ticket document. No `CommentRepository` or comments collection.
- **Agent (separate document):** `id`, `name`, `email`; seeded as initial development records.
- **TicketStatus:** `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`. Allowed transitions: `OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED`, plus `RESOLVED -> IN_PROGRESS`. An assignee is required to enter `IN_PROGRESS`; `CLOSED` is terminal.
- **Priority:** `LOW`, `MEDIUM`, `HIGH`; selected at creation and immutable thereafter.
- **TicketService:** coordinates repositories, verifies agent ID exists before assignment, assigns backend-generated IDs/timestamps, loads/saves ticket aggregates.
- **TicketRepository / AgentRepository:** Spring Data MongoDB repositories.
- **TicketController and DTOs:** expose HTTP requests/responses without exposing persistence models directly. A centralized exception handler maps validation and domain exceptions to consistent HTTP responses.

**Domain method sketch (implementation-independent):** `Ticket.assignAgent(agentId, at)`, `Ticket.changeStatus(nextStatus, at)`, and `Ticket.addComment(commentId, authorName, content, at)`. Timestamp and comment-ID parameters come from the application layer, ensuring tests can control them. Ticket identity generation will be finalized alongside persistence mapping.

**Consistency notes:** D8=A matches the previously approved US-06 acceptance criterion (comments embedded in tickets). The earlier proposal to add a comments collection is **not** part of the approved design. Agent existence cannot be checked by the `Ticket` object alone; that check belongs in `TicketService`. A `Comment` is a separate Java class but not a separate MongoDB collection.

**Evidence of human supervision:** The AI advisor compared alternatives. The human developer explicitly approved all D7–D14 choices and amended D8 from B to A after reviewing the trade-offs. Preserve both the revision and its final resolution when reporting AI-assisted decision-making.

**Next:** Phase 2 Step 3 — MongoDB Data Model.


## Step 3: MongoDB Data Model

**Date:** 2026-11-19  
**Decision status:** Approved by human developer  
**Stage:** Analysis and Design; no implementation or test results claimed

| ID | Human-approved decision | Alternative considered | Rationale | Trade-off / consequence |
|---|---|---|---|---|
| D15 | Generate Java UUID values and store their textual (36-character) representation as MongoDB `_id` | Use MongoDB ObjectIds | Database-independent string identifiers; easy to create and test | Longer keys than ObjectId; choose explicit string mapping |
| D16 | Apply Bean Validation to request DTOs **and** enforce invariants within domain objects | Validate only at controllers | Protects external input and internal domain changes | Validation must be consistent at both boundaries |
| D17 | Create no secondary indexes initially; retain only MongoDB's default `_id` index on each collection | Index `tickets.createdAt` upfront | MVP has low expected record volume | Unindexed newest-first sorting may not scale; revisit based on measurements |
| D18 | Use `@Version Long version` on `Ticket` with optimistic locking | Ordinary saves without conflict detection | Prevents silent overwrites of full ticket documents containing embedded comments | Concurrent saves may fail; service/global error handling must return a safe conflict response; tests must cover stale writes |
| D19 | Seed a small predetermined list of agents idempotently by stable IDs, adding only missing records | Insert agents manually in Atlas | Predictable, reproducible development/demo setup | Seed logic must avoid duplicates, avoid overwriting changed records and handle concurrent initialization safely |

### Persistence mapping and rules

- `tickets` and `agents` are the **only** MongoDB collections. `Comment` is an embedded object within `Ticket.comments`; no comment collection or repository.
- `Ticket._id`, `Agent._id` and `Comment.id` are generated on the backend using UUID string values (the embedded Comment identifier is just an object field). Use explicit string Mongo mapping for `_id` where helpful (`@MongoId(FieldType.STRING)`), ensuring consistency.
- `Ticket.version` is a `Long` annotated `@Version`, managed by Spring Data MongoDB; application/domain methods do not modify it. Configure acknowledged writes to reliably observe version conflicts. Handle `OptimisticLockingFailureException` as an API conflict (`409`), without silently discarding the user's work.
- Timestamps are Java `Instant` values produced by a controllable application clock, persisted as MongoDB BSON Dates. `createdAt` stays fixed; `updatedAt` changes on valid mutations.
- `status` and `priority` are saved as enum strings. New tickets start `OPEN`; priority is immutable after creation. `assignedAgentId` is a nullable string and must refer to an existing seeded agent if present (checked in service code; MongoDB does not enforce foreign keys).
- The `Ticket` aggregate controls `assignAgent`, `changeStatus`, and `addComment`. A closed ticket cannot be edited. The service validates agent existence, supplies IDs/times and saves via repositories.
- For a new ticket, initialize `comments` as an empty list; on `addComment` append an embedded value and persist the ticket once. Concurrency conflict tests are essential because full ticket documents are written.
- No custom indexes or schema migration system will be added solely for the MVP. Test sorting by creation date with low volume; revisit indexes only with evidence of a performance need.
- Seeder should add stable agent records **only when missing** and never reset the collection or overwrite existing agent records; use fixed UUIDs and inserts/conditional upserts to keep the process idempotent.

### UML design impact

Add `-Long version` to `Ticket` (persistence-managed, no public setter). The domain remains `Ticket 1 *-- 0..* Comment` and `Ticket ..> Agent` (reference-by-ID). There is no `CommentRepository`. No changes to functional scope or Jira stories are necessary.

### Risks to verify during implementation

1. Stale ticket saves must trigger an optimistic locking failure with acknowledged writes and be mapped to HTTP 409.
2. Comment changes must preserve earlier comments and update `updatedAt`.
3. Seeder must be idempotent across repeated application starts and not replace edited agents.
4. No database credentials in Git; each team member may use their own Atlas database and shared integration database as separately configured.
5. With only `_id` indexes, newest-first listing should be tested at MVP scale, not represented as proven for large workloads.

**Evidence:** The human developer explicitly approved D15=A, D16=A, D17=B, D18=A, D19=A. AI suggested the options and evaluated consequences. The decisions are design approvals only, not proof of working code or tests.

**Next:** Step 4 — REST API Design.

## Step 4: REST API Design

**Date:** 2026-11-19  
**Decision status:** Approved by human developer  
**Stage:** Design contract only; endpoints are not yet implemented or tested.

| ID | Human-approved choice | Alternative considered | Rationale | Trade-off / consequence |
|---|---|---|---|---|
| D20 | REST resource-oriented endpoints under `/api` | Action URLs such as `/createTicket` | Consistent paths and standard HTTP semantics | Requires deliberate naming, especially sub-resources for workflow operations |
| D21 | Separate `PATCH /tickets/{id}/assignment` and `/status` operations | Generic PATCH accepting arbitrary ticket fields | Restricts updates to approved business operations, makes rules explicit | More routes and specialized DTOs than a generic PATCH |
| D22 | `GET /api/agents` retrieves seeded agents | Hardcode agent data in React | Keeps UI aligned with MongoDB; provides real agent IDs for assignment | Additional read-only endpoint and service mapping |
| D23 | Standardized RFC 9457 Problem Details responses | Independent custom error formats per controller | Consistent error contract for frontend and testing | Global handler must map domain/framework errors carefully and avoid leaking sensitive details |
| D24 | Return useful JSON after successful operations (`201` creation, `200` update) | Return `204` and force re-fetch | UI receives fresh data/version and avoids unnecessary reads | Extra response DTO mapping and slightly larger responses |
| D25 | React displays conflicts and requires user-driven reload/retry | Automatically retry conflicts | Avoids unintended duplicate/stale actions | Requires an explicit reload/retry interaction |

### Derived API design details to review in Step 5

The six decisions above were explicitly approved by the human developer. The following concrete contract choices are derived from those decisions and the existing requirements, and should be checked for consistency in **Step 5**:

1. Seven endpoints: `POST /api/tickets`, `GET /api/tickets`, `GET /api/tickets/{id}`, `PATCH /api/tickets/{id}/assignment`, `PATCH /api/tickets/{id}/status`, `POST /api/tickets/{id}/comments`, `GET /api/agents`.
2. DTOs: `CreateTicketRequest`, `AssignTicketRequest`, `ChangeTicketStatusRequest`, `AddCommentRequest`, `TicketSummaryResponse`, `TicketDetailResponse`, `CommentResponse`, `CommentCreatedResponse`, `AgentSummaryResponse`.
3. **Critical concurrency refinement:** Requests that mutate a ticket carry `expectedVersion`. The service checks this against the latest stored version before applying a domain operation; MongoDB `@Version` still protects the read/write race. Both produce HTTP `409 VERSION_CONFLICT`. Merely using `@Version` without the client precondition would not reliably flag an outdated browser's request.
4. `GET /api/tickets` is newest-first, as required by US-02. Ticket details return chronological comments; nullable assignment is rendered as null and React can show "Unassigned".
5. Centralized errors use RFC 9457 `application/problem+json`, with `400` for input errors, `404` for unknown resources, and `409` for workflow and concurrency conflicts. Successful create/comment returns `201` and updated ticket operations return `200`.
6. Comment creation returns `ticketVersion` as well as the created comment, preventing the next client operation from using an obsolete version.
7. No authentication or role enforcement is claimed; there is no generic edit-ticket endpoint or priority-changing API.

**Detailed contract:** [`docs/design/REST_API_CONTRACT.md`](design/REST_API_CONTRACT.md). It includes JSON examples, DTO fields, error mapping and acceptance-test traceability. The API contract is a design artifact, not evidence of deployed software.

### Trade-off assessment

All D20–D25 selections match the AI recommendations. The explicit-version request fields increase payloads and frontend state handling, but they are needed to satisfy the chosen optimistic concurrency behavior and safe manual retry experience. Specialized routes/DTOs add some code; that is justified by the project's workflow rules and testing requirements.

### Evidence of human supervision

The AI advisor presented six alternatives and the human developer expressly approved D20=A through D25=A on 2026-11-19. The advisor then clarified the optimistic concurrency contract and mapped status/error behavior into concrete DTOs for review. Final implementation and tests are still pending.

**Step 5 resolution:** See the approved R1–R4 review record below and `docs/design/DESIGN_REVIEW.md`.


## Step 5: Cross-document design review (R1–R4)

**Date:** 2026-11-19  
**Decision authority:** Human developer, explicitly approving R1=A, R2=A, R3=A, R4=A.  
**Context:** Review of requirements FR-01–FR-06 against system architecture, UML, MongoDB schema and REST API contract. Changes are limited to implementation clarity, validation and safety; no new user story is introduced.

| ID | Approved correction | Alternative considered | Reason | Trade-off |
|---|---|---|---|---|
| R1 | `AgentController` and `AgentService` expose read-only `GET /api/agents` | Controller accesses `AgentRepository` directly | Aligns support endpoint with approved layered design | Two extra small classes; improved separation and testing |
| R2 | Limit ticket title to 120, description to 2000, requester name to 100; comment author to 100 and content to 2000 characters | Nonblank validation only | Clear, testable input boundaries and controlled document growth | Rejects unusually long text; updating requirements and DTO/domain tests required |
| R3 | Measure validation, tests, and responsive UI: 375px viewport, error feedback, workflow unit tests and per-story API integration checks | Leave NFRs qualitative | Makes quality requirements verifiable for the professor | Additional validation and test work |
| R4 | Local development and controlled academic demo only | Public unauthenticated deployment | Mitigates risk from deliberately missing authentication/authorization | Demo accessibility is limited; deployment plan must be revisited before public hosting |

**Consistency actions:** Add `AgentController` and `AgentService` to the UML/system architecture; update `docs/REQUIREMENTS.md`, field constraints in `docs/design/REST_API_CONTRACT.md` and `docs/design/MONGODB_DATA_MODEL.md`; record measurable NFRs and demo limitations; preserve `expectedVersion`, `@Version`, embedded comments and all seven API operations unchanged.

**Evidence:** Design review matrix and implementation handoff in `docs/design/DESIGN_REVIEW.md`. The developer accepted all four proposed corrections. Design review does not mean implementation or testing has occurred. Jira `TIC-3` and `TIC-8` acceptance criteria should be synchronized with the agreed maximum lengths when next edited.
