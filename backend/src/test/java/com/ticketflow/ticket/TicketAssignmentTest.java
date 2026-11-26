package com.ticketflow.ticket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.ticketflow.ticket.TicketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TicketAssignmentTest {

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"OPEN", "IN_PROGRESS", "RESOLVED"})
    void shouldAssignInEveryPermittedStatusAndPreserveOtherState(TicketStatus status) {
        Ticket ticket = ticket(status, 4L, null);
        comments(ticket);
        List<Comment> originalComments = ticket.getComments();

        ticket.assignAgent(AGENT_ID, UPDATED);

        assertAll(
                () -> assertEquals(AGENT_ID, ticket.getAssignedAgentId()),
                () -> assertEquals(UPDATED, ticket.getUpdatedAt()),
                () -> assertEquals(status, ticket.getStatus()),
                () -> assertEquals(Priority.HIGH, ticket.getPriority()),
                () -> assertEquals(CREATED, ticket.getCreatedAt()),
                () -> assertEquals(TICKET_ID, ticket.getId()),
                () -> assertEquals("Printer problem", ticket.getTitle()),
                () -> assertEquals("Printer is offline", ticket.getDescription()),
                () -> assertEquals("Test User", ticket.getRequesterName()),
                () -> assertEquals(originalComments, ticket.getComments()),
                () -> assertEquals(4L, ticket.getVersion())
        );
    }

    @Test
    void shouldReplaceExistingAgent() {
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS, 4L, OTHER_AGENT_ID);

        ticket.assignAgent(AGENT_ID, UPDATED);

        assertEquals(AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(UPDATED, ticket.getUpdatedAt());
    }

    @Test
    void shouldAcceptSameAgentAndUpdateTimestampWithoutChangingVersion() {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, AGENT_ID);

        ticket.assignAgent(AGENT_ID, UPDATED);

        assertEquals(AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(UPDATED, ticket.getUpdatedAt());
        assertEquals(4L, ticket.getVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {AGENT_ID, OTHER_AGENT_ID})
    void shouldRejectAssignmentAndReassignmentOnClosedTicket(String agentId) {
        Ticket ticket = ticket(TicketStatus.CLOSED, 4L, AGENT_ID);
        comments(ticket);
        List<Comment> before = ticket.getComments();

        assertThrows(TicketClosedException.class, () -> ticket.assignAgent(agentId, UPDATED));

        assertEquals(AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertEquals(4L, ticket.getVersion());
        assertEquals(before, ticket.getComments());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "1-1-1-1-1"})
    void shouldRejectInvalidAgentWithoutPartialMutation(String agentId) {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, OTHER_AGENT_ID);

        assertThrows(IllegalArgumentException.class, () -> ticket.assignAgent(agentId, UPDATED));

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        assertEquals(4L, ticket.getVersion());
    }

    @Test
    void shouldRejectMissingTimestampWithoutPartialMutation() {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, OTHER_AGENT_ID);

        assertThrows(IllegalArgumentException.class, () -> ticket.assignAgent(AGENT_ID, null));

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
    }
}
