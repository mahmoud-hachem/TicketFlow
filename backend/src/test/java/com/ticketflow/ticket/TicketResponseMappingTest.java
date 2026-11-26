package com.ticketflow.ticket;

import com.ticketflow.agent.Agent;
import com.ticketflow.agent.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.ticketflow.ticket.TicketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketResponseMappingTest {

    private TicketRepository tickets;
    private AgentRepository agents;
    private TicketService service;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        agents = mock(AgentRepository.class);
        service = new TicketService(tickets, agents, Clock.fixed(UPDATED, ZoneOffset.UTC),
                () -> TICKET_ID);
    }

    @Test
    void shouldBatchDistinctAgentsAndPreserveRepositoryTicketOrdering() {
        Ticket newest = ticket(TicketStatus.OPEN, 3L, AGENT_ID);
        Ticket tiedFirst = ticket(TicketStatus.OPEN, 2L, OTHER_AGENT_ID);
        Ticket tiedSecond = ticket(TicketStatus.OPEN, 1L, AGENT_ID);
        Ticket unassigned = ticket(TicketStatus.OPEN, 0L, null);
        ReflectionTestUtils.setField(newest, "id", "newest");
        ReflectionTestUtils.setField(newest, "createdAt", UPDATED);
        ReflectionTestUtils.setField(tiedFirst, "id", "a-tied");
        ReflectionTestUtils.setField(tiedSecond, "id", "b-tied");
        ReflectionTestUtils.setField(unassigned, "id", "oldest");
        ReflectionTestUtils.setField(unassigned, "createdAt", CREATED.minusSeconds(1));
        when(tickets.findAllByOrderByCreatedAtDescIdAsc())
                .thenReturn(List.of(newest, tiedFirst, tiedSecond, unassigned));
        when(agents.findAllById(Set.of(AGENT_ID, OTHER_AGENT_ID))).thenReturn(List.of(
                new Agent(OTHER_AGENT_ID, "Second Agent", "second@example.com"),
                new Agent(AGENT_ID, "First Agent", "first@example.com")));

        List<TicketSummaryResponse> result = service.listTickets();

        assertEquals(List.of("newest", "a-tied", "b-tied", "oldest"),
                result.stream().map(TicketSummaryResponse::id).toList());
        assertEquals("First Agent", result.get(0).assignedAgent().name());
        assertEquals(OTHER_AGENT_ID, result.get(1).assignedAgent().id());
        assertEquals(AGENT_ID, result.get(2).assignedAgent().id());
        assertNull(result.get(3).assignedAgent());
        verify(tickets).findAllByOrderByCreatedAtDescIdAsc();
        verify(agents).findAllById(Set.of(AGENT_ID, OTHER_AGENT_ID));
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldMapRealDetailsAndCommentsWithoutReorderingStoredComments() {
        Ticket ticket = ticket(TicketStatus.RESOLVED, 4L, AGENT_ID);
        comments(ticket);
        List<Comment> before = ticket.getComments();
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(agents.findById(AGENT_ID)).thenReturn(Optional.of(
                new Agent(AGENT_ID, "Actual Agent", "private@example.com")));

        TicketDetailResponse response = service.getTicketById(TICKET_ID);

        assertEquals("Actual Agent", response.assignedAgent().name());
        assertEquals(AGENT_ID, response.assignedAgent().id());
        assertEquals(List.of("First author", "Second author", "Third author"),
                response.comments().stream().map(CommentResponse::authorName).toList());
        assertEquals(List.of("First tied comment", "Second tied comment", "Later comment"),
                response.comments().stream().map(CommentResponse::content).toList());
        assertEquals(CREATED, response.comments().get(0).createdAt());
        assertEquals(before, ticket.getComments());
        verify(tickets).findById(TICKET_ID);
        verify(agents).findById(AGENT_ID);
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldNotQueryAgentsForUnassignedOrEmptyListsAndDetails() {
        Ticket ticket = ticket(TicketStatus.OPEN, 0L, null);
        when(tickets.findAllByOrderByCreatedAtDescIdAsc())
                .thenReturn(List.of(ticket)).thenReturn(List.of());
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertNull(service.listTickets().get(0).assignedAgent());
        assertTrue(service.listTickets().isEmpty());
        assertNull(service.getTicketById(TICKET_ID).assignedAgent());
        assertTrue(service.getTicketById(TICKET_ID).comments().isEmpty());

        verifyNoInteractions(agents);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectDanglingReferencesInListAndDetails(boolean list) {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, AGENT_ID);
        when(tickets.findAllByOrderByCreatedAtDescIdAsc()).thenReturn(List.of(ticket));
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThrows(TicketIntegrityException.class,
                () -> { if (list) service.listTickets(); else service.getTicketById(TICKET_ID); });

        assertEquals(AGENT_ID, ticket.getAssignedAgentId());
        verify(tickets, never()).save(any(Ticket.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectNullStoredVersionsOnReadsWithoutMigration(boolean list) {
        Ticket ticket = ticket(TicketStatus.OPEN, null, null);
        when(tickets.findAllByOrderByCreatedAtDescIdAsc()).thenReturn(List.of(ticket));
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThrows(TicketIntegrityException.class,
                () -> { if (list) service.listTickets(); else service.getTicketById(TICKET_ID); });

        assertNull(ticket.getVersion());
        verify(tickets, never()).save(any(Ticket.class));
        verifyNoInteractions(agents);
    }
}
