package com.ticketflow.ticket;

import com.ticketflow.agent.Agent;
import com.ticketflow.agent.AgentNotFoundException;
import com.ticketflow.agent.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.ticketflow.ticket.TicketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketAssignmentServiceTest {

    private TicketRepository tickets;
    private AgentRepository agents;
    private TicketService service;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        agents = mock(AgentRepository.class);
        when(agents.existsById(OTHER_AGENT_ID)).thenReturn(true);
        service = new TicketService(tickets, agents, Clock.fixed(UPDATED, ZoneOffset.UTC),
                () -> TICKET_ID);
    }

    private void availableTicket(Ticket ticket) {
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
    }

    private void availableAgent() {
        when(agents.findById(AGENT_ID)).thenReturn(Optional.of(
                new Agent(AGENT_ID, "Demo Agent 1", "agent1@example.com")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {OTHER_AGENT_ID, AGENT_ID})
    void shouldAssignReassignAndAcceptSameAgentThroughNormalSave(String previousAgent) {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, previousAgent);
        comments(ticket);
        List<Comment> originalComments = ticket.getComments();
        availableTicket(ticket);
        availableAgent();
        when(tickets.save(ticket)).thenAnswer(invocation -> {
            // Simulate persistence-managed versioning; real MongoDB remains untested.
            ReflectionTestUtils.setField(ticket, "version", 5L);
            return ticket;
        });

        TicketDetailResponse response = service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L));

        assertEquals(AGENT_ID, response.assignedAgent().id());
        assertEquals("Demo Agent 1", response.assignedAgent().name());
        assertEquals(5L, response.version());
        assertEquals(UPDATED, response.updatedAt());
        assertEquals(CREATED, response.createdAt());
        assertEquals(TicketStatus.OPEN, response.status());
        assertEquals(Priority.HIGH, response.priority());
        assertEquals(originalComments, ticket.getComments());
        assertEquals(List.of("First tied comment", "Second tied comment", "Later comment"),
                response.comments().stream().map(CommentResponse::content).toList());
        InOrder order = inOrder(tickets, agents);
        order.verify(tickets).findById(TICKET_ID);
        if (OTHER_AGENT_ID.equals(previousAgent)) {
            order.verify(agents).existsById(OTHER_AGENT_ID);
        }
        order.verify(agents).findById(AGENT_ID);
        order.verify(tickets).save(ticket);
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldReturnActualRepositoryResultAndVersion() {
        Ticket loaded = ticket(TicketStatus.OPEN, 4L, null);
        Ticket saved = ticket(TicketStatus.OPEN, 12L, AGENT_ID);
        saved.assignAgent(AGENT_ID, UPDATED);
        availableTicket(loaded);
        availableAgent();
        when(tickets.save(loaded)).thenReturn(saved);

        TicketDetailResponse response = service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L));

        assertEquals(12L, response.version());
        assertEquals(4L, loaded.getVersion());
        assertEquals(UPDATED, response.updatedAt());
        verify(tickets).save(loaded);
    }

    @Test
    void shouldRejectUnknownTicketWithoutAgentLookupOrSave() {
        assertThrows(TicketNotFoundException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L)));

        verify(tickets).findById(TICKET_ID);
        verifyNoMoreInteractions(tickets);
        verifyNoInteractions(agents);
    }

    @ParameterizedTest
    @ValueSource(longs = {3, 5})
    void shouldRejectStaleOrFutureExpectedVersionBeforeMutation(long expectedVersion) {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, OTHER_AGENT_ID);
        availableTicket(ticket);

        assertThrows(TicketVersionConflictException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, expectedVersion)));

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        assertEquals(4L, ticket.getVersion());
        verify(tickets).findById(TICKET_ID);
        verifyNoMoreInteractions(tickets);
        verifyNoInteractions(agents);
    }

    @Test
    void shouldRejectUnknownAgentWithoutMutationOrSave() {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, OTHER_AGENT_ID);
        availableTicket(ticket);

        assertThrows(AgentNotFoundException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L)));

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets).findById(TICKET_ID);
        verify(agents).existsById(OTHER_AGENT_ID);
        verify(agents).findById(AGENT_ID);
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldRejectClosedTicketWithoutMutationOrSave() {
        Ticket ticket = ticket(TicketStatus.CLOSED, 4L, OTHER_AGENT_ID);
        availableTicket(ticket);
        availableAgent();

        assertThrows(TicketClosedException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L)));

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets).findById(TICKET_ID);
        verify(agents).existsById(OTHER_AGENT_ID);
        verify(agents).findById(AGENT_ID);
        verifyNoMoreInteractions(tickets, agents);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1})
    void shouldRejectInconsistentStoredVersionWithoutMutationOrSave(Long storedVersion) {
        Ticket ticket = ticket(TicketStatus.OPEN, storedVersion, OTHER_AGENT_ID);
        availableTicket(ticket);

        assertThrows(TicketIntegrityException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 0L)));

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        assertEquals(storedVersion, ticket.getVersion());
        verify(tickets).findById(TICKET_ID);
        verifyNoMoreInteractions(tickets);
        verifyNoInteractions(agents);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1})
    void shouldRejectMissingOrNegativeExpectedVersionForDirectCalls(Long expectedVersion) {
        assertThrows(IllegalArgumentException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, expectedVersion)));

        verifyNoInteractions(tickets, agents);
    }

    @Test
    void shouldPropagateOptimisticLockFailureWithoutRetry() {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, null);
        comments(ticket);
        List<Comment> before = ticket.getComments();
        availableTicket(ticket);
        availableAgent();
        OptimisticLockingFailureException failure =
                new OptimisticLockingFailureException("Competing write");
        when(tickets.save(ticket)).thenThrow(failure);

        assertSame(failure, assertThrows(OptimisticLockingFailureException.class,
                () -> service.assignAgent(TICKET_ID, new AssignTicketRequest(AGENT_ID, 4L))));

        assertEquals(4L, ticket.getVersion());
        assertEquals(before, ticket.getComments());
        verify(tickets).findById(TICKET_ID);
        verify(agents).findById(AGENT_ID);
        verify(tickets).save(ticket);
        verifyNoMoreInteractions(tickets, agents);
    }
}
