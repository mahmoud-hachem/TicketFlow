# TicketFlow

TicketFlow is a small web-based support-ticket management application developed for the **Agentic Software Engineering** course. The project aims to deliver a working object-oriented application while demonstrating human-led engineering decisions, iterative development, testing and responsible AI assistance.

## MVP scope

1. Create a support ticket (title, description, requester name and priority).
2. View all tickets.
3. View a ticket's details.
4. Assign or reassign an existing support agent.
5. Update ticket status using defined business rules.
6. Add and view ticket comments.

**Out of scope:** user login and permission enforcement, email notifications, attachments, search/filtering and analytics dashboards. Requester and agent are conceptual roles; identities are **not** authenticated in this MVP.

## Planned technical stack

| Layer | Choice |
| --- | --- |
| Frontend | React + Vite |
| Backend | Java 21, Spring Boot, Maven |
| Architecture | Layered modular monolith (one backend, feature-oriented organization) |
| API | REST + JSON |
| Database | MongoDB Atlas + Spring Data MongoDB (not JPA) |
| Testing | JUnit and Spring Boot Test; further tools where needed |
| Planning | Jira (`TIC` project) |
| Version control | Git + GitHub |
| AI assistance | ChatGPT for planning; Codex for architecture advice and planned coding assistance, always human-reviewed |

These technologies reflect **approved design decisions**, not a claim that the application is already implemented.

## Planning & delivery

The backlog has two epics (Ticket Management and Ticket Resolution Workflow), six stories (`TIC-3`–`TIC-8`), and two technical tasks (`TIC-9`, `TIC-10`).

| Sprint | Goal | Work |
| --- | --- | --- |
| Sprint 1 — Foundation & Tickets | Initialize the system; create and view tickets | `TIC-9`, `TIC-3`, `TIC-4`, `TIC-5` |
| Sprint 2 — Workflow & Completion | Assignment, status, comments, integration and review | `TIC-6`, `TIC-7`, `TIC-8`, `TIC-10` |

The initial planning estimates are **18 person-hours** for Sprint 1 and **19 person-hours** for Sprint 2. They are provisional estimates, not recorded actual effort. Refer to Jira for the live backlog and real sprint status.

## Documentation

- [Requirements and acceptance criteria](docs/REQUIREMENTS.md)
- [ADR-001 — Application architecture and database decision](docs/decisions/ADR-001.md)

## Development status

Phase 1 defined the scope, requirements, architecture, and initial Jira backlog. The application, actual test results, screenshots and setup commands will be documented **only after implementation and verification**.

## Running TicketFlow


### Prerequisites

- Java 21
- Node.js 24
- npm
- Git
- MongoDB Atlas access

Maven is provided through the Maven Wrapper.

### Backend setup (Windows)

1. Open a terminal in the `backend` directory.
2. Configure the `MONGODB_URI` environment variable using authorized MongoDB Atlas credentials.
3. Run:

   `mvnw.cmd spring-boot:run`

The backend runs at http://localhost:8080.

### Frontend setup

1. Open a separate terminal in the `frontend` directory.
2. Run `npm ci`.
3. Run `npm run dev`.

The frontend runs at http://localhost:5173.

### Database configuration

MongoDB Atlas is used for persistence. Each developer must have authorized database credentials and an allowed IP address.

Database credentials must never be committed to GitHub.

### Demo agent seeding (TIC-6, Increment 1)

`GET /api/agents` returns stored agents as `id` and `name`, ordered by name and then ID. Emails are not returned.

Demo seeding is **disabled by default** through `ticketflow.demo-seeding.enabled=false`. To opt in for an authorized development/demo database, run `mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--ticketflow.demo-seeding.enabled=true"` from the backend directory. Omit that argument or pass `false` to restore the default on the next startup, and check that no other configuration enables it. Keep MongoDB credentials outside command-line arguments and Git.

When enabled, startup inserts only missing records for Demo Agent 1, Demo Agent 2 and Demo Agent 3 (`agent1@example.com` through `agent3@example.com`) using fixed UUIDs. It never updates/deletes existing agents or seeds tickets. Disabling seeding does not remove previously inserted agents. Do not enable it against the shared Atlas database; real MongoDB verification requires a separately configured and authorized isolated test database.

### Frontend/backend communication

The Vite development proxy forwards `/api` requests to the Spring Boot backend on port 8080.

### Implementation status

TIC-3 through TIC-8 implement all six MVP features. TIC-10 isolated checks pass; real MongoDB verification remains pending explicit authorization of an isolated test database.

### Verification

Run `mvn test` from `backend` for isolated tests only, and `npm run lint` / `npm run build` from `frontend`. Database-dependent tests are excluded from the default Maven run and guarded against shared-database fallback. See [TIC-10 verification and database authorization requirements](docs/TIC10_VERIFICATION.md) before considering the separate integration profile. Do not execute database tests until the human developer authorizes the exact isolated target.
## Security note

MongoDB credentials must be provided through environment variables or local configuration that is excluded from Git. Never commit secrets or real database connection strings.
