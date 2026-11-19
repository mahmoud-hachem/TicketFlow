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

_To be added after the Spring Boot and React projects are initialized and verified in `TIC-9`._

## Security note

MongoDB credentials must be provided through environment variables or local configuration that is excluded from Git. Never commit secrets or real database connection strings.
