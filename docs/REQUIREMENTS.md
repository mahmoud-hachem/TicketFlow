# TicketFlow — MVP Requirements

**Planning baseline:** 19 November 2026  
**Status:** Approved for the initial mini-project MVP; changes should be recorded in Jira and version control.

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
| BR-03 | Ticket title, description and requester name must not be blank. |
| BR-04 | One ticket has no more than one assigned agent at a time; reassignment replaces the existing assignment. |
| BR-05 | An agent must exist before assignment; nonexistent agents are rejected. |
| BR-06 | A ticket must be assigned before it can move to `IN_PROGRESS`. |
| BR-07 | Allowed status transitions are `OPEN → IN_PROGRESS`, `IN_PROGRESS → RESOLVED`, `RESOLVED → CLOSED`, and `RESOLVED → IN_PROGRESS`. Other direct transitions are rejected. |
| BR-08 | A `CLOSED` ticket is final: no assignment changes, status changes or new comments. |
| BR-09 | A ticket may have multiple comments. Author name and message cannot be blank. |
| BR-10 | Creation timestamps are assigned by the backend. Comments display chronologically. |

## 4. Acceptance criteria (minimum)

### TIC-3 — Create Support Ticket
- A valid title, description, requester and priority create a persisted ticket with an ID and `OPEN` status.
- Missing/blank required fields or invalid priority return clear validation errors.

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
- Empty comments, nonexistent tickets and comments on closed tickets are rejected.

## 5. Initial domain model

- **Ticket:** id, title, description, requesterName, priority, status, assignedAgentId (optional), createdAt, updatedAt, comments.
- **Agent:** id, name, email. Initial agents can be seeded as demo records.
- **Comment:** id, authorName, content, createdAt. Comments are embedded in Ticket documents.
- **Enums:** TicketStatus (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`) and Priority (`LOW`, `MEDIUM`, `HIGH`).

## 6. Non-functional requirements

- **Maintainability:** clear separation between API, business rules and persistence.
- **Quality:** automated unit tests for workflow rules and integration tests for important API paths.
- **Usability:** understandable forms, status feedback and responsive pages.
- **Data validation:** invalid input must not produce invalid stored records.
- **Security:** MongoDB credentials never committed; no claim of role enforcement without authentication.
- **Traceability:** Jira stories, meaningful Git commits and evidence of review and AI-assisted development.

## 7. Implementation notes (proposals, not yet built)

Potential REST endpoints: `POST /api/tickets`, `GET /api/tickets`, `GET /api/tickets/{id}`, `PATCH /api/tickets/{id}/assignment`, `PATCH /api/tickets/{id}/status`, `POST /api/tickets/{id}/comments`, and `GET /api/agents`. Final request/response contracts will be selected when implementing the relevant stories.
