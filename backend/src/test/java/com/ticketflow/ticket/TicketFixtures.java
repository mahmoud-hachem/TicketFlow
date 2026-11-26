package com.ticketflow.ticket;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class TicketFixtures {
    static final String TICKET_ID = "b8f6ee9f-bf96-45d2-ac64-e6b1f208301a";
    static final String AGENT_ID = "d50c63a2-85af-424f-9c6e-dc90f7f303f9";
    static final String OTHER_AGENT_ID = "123c20bb-0cc9-4d62-8cc5-2bb7e05e5664";
    static final Instant CREATED = Instant.parse("2026-11-25T10:00:00Z");
    static final Instant UPDATED = Instant.parse("2026-11-26T10:00:00Z");

    private TicketFixtures() {
    }

    // Hydrate persisted states in tests without adding production state setters.
    static Ticket ticket(TicketStatus status, Long version, String agentId) {
        Ticket ticket = new Ticket(TICKET_ID, "Printer problem", "Printer is offline",
                "Test User", Priority.HIGH, CREATED);
        ReflectionTestUtils.setField(ticket, "status", status);
        ReflectionTestUtils.setField(ticket, "version", version);
        ReflectionTestUtils.setField(ticket, "assignedAgentId", agentId);
        return ticket;
    }

    static void comments(Ticket ticket) {
        ReflectionTestUtils.setField(ticket, "comments", new ArrayList<>(List.of(
                new Comment("00000000-0000-4000-8000-000000000003", "Third author",
                        "Later comment", CREATED.plusSeconds(1)),
                new Comment("00000000-0000-4000-8000-000000000002", "Second author",
                        "Second tied comment", CREATED),
                new Comment("00000000-0000-4000-8000-000000000001", "First author",
                        "First tied comment", CREATED)
        )));
    }
}
