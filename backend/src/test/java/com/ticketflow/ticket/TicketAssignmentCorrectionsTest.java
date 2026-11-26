package com.ticketflow.ticket;

import com.ticketflow.agent.Agent;
import com.ticketflow.agent.AgentNotFoundException;
import com.ticketflow.agent.AgentRepository;
import com.ticketflow.config.ApplicationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ticketflow.ticket.TicketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketAssignmentCorrectionsTest {

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectDanglingCurrentAssignmentIncludingSameAgentWithoutMutation(boolean sameAgent) {
        String currentAgentId = sameAgent ? AGENT_ID : OTHER_AGENT_ID;
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, currentAgentId);
        comments(ticket);
        List<Comment> before = ticket.getComments();
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThrows(TicketIntegrityException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L)));

        assertEquals(currentAgentId, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        assertEquals(4L, ticket.getVersion());
        assertEquals(before, ticket.getComments());
        verify(tickets).findById(TICKET_ID);
        if (sameAgent) {
            verify(agents).findById(AGENT_ID);
        } else {
            verify(agents).existsById(OTHER_AGENT_ID);
        }
        verifyNoMoreInteractions(tickets, agents);
    }

    static Stream<Arguments> invalidArguments() {
        AssignTicketRequest valid = new AssignTicketRequest(AGENT_ID, 4L);
        return Stream.of(
                Arguments.of(TICKET_ID, null),
                Arguments.of(null, valid),
                Arguments.of("", valid),
                Arguments.of(" ", valid),
                Arguments.of("invalid", valid),
                Arguments.of("1-1-1-1-1", valid),
                Arguments.of(" " + TICKET_ID, valid),
                Arguments.of(TICKET_ID, new AssignTicketRequest(null, 4L)),
                Arguments.of(TICKET_ID, new AssignTicketRequest("", 4L)),
                Arguments.of(TICKET_ID, new AssignTicketRequest(" ", 4L)),
                Arguments.of(TICKET_ID, new AssignTicketRequest("invalid", 4L)),
                Arguments.of(TICKET_ID, new AssignTicketRequest("1-1-1-1-1", 4L)),
                Arguments.of(TICKET_ID, new AssignTicketRequest(" " + AGENT_ID, 4L)),
                Arguments.of(TICKET_ID, new AssignTicketRequest(AGENT_ID, null)),
                Arguments.of(TICKET_ID, new AssignTicketRequest(AGENT_ID, -1L))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    void shouldRejectInvalidDirectArgumentsBeforeRepositoryInteraction(
            String id, AssignTicketRequest request
    ) {
        assertThrows(InvalidTicketInputException.class, () -> service.assignAgent(id, request));

        verifyNoInteractions(tickets, agents);
    }

    @Test
    void shouldNotNormalizeTargetAgentId() {
        String uppercase = AGENT_ID.toUpperCase(Locale.ROOT);
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, null);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        Map<String, Agent> stored = Map.of(AGENT_ID,
                new Agent(AGENT_ID, "Stored Agent", "agent@example.com"));
        when(agents.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(0))));

        assertThrows(AgentNotFoundException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(uppercase, 4L)));

        assertNull(ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets).findById(TICKET_ID);
        verify(agents).findById(uppercase);
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldNotNormalizeExistingAgentReference() {
        String uppercase = OTHER_AGENT_ID.toUpperCase(Locale.ROOT);
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, uppercase);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(agents.existsById(anyString())).thenAnswer(invocation ->
                OTHER_AGENT_ID.equals(invocation.getArgument(0)));

        assertThrows(TicketIntegrityException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L)));

        assertEquals(uppercase, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets).findById(TICKET_ID);
        verify(agents).existsById(uppercase);
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldPreserveAnExactlyMatchingExistingUppercaseIdentifier() {
        String uppercase = AGENT_ID.toUpperCase(Locale.ROOT);
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, uppercase);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(agents.findById(uppercase)).thenReturn(Optional.of(
                new Agent(uppercase, "Existing Agent", "agent@example.com")));
        when(tickets.save(ticket)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(ticket, "version", 5L);
            return ticket;
        });

        TicketDetailResponse response = service.assignAgent(TICKET_ID,
                new AssignTicketRequest(uppercase, 4L));

        assertEquals(uppercase, response.assignedAgent().id());
        assertEquals(uppercase, ticket.getAssignedAgentId());
        assertEquals(5L, response.version());
        verify(tickets).findById(TICKET_ID);
        verify(agents).findById(uppercase);
        verify(tickets).save(ticket);
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldNotNormalizeTicketId() {
        String uppercase = TICKET_ID.toUpperCase(Locale.ROOT);

        assertThrows(TicketNotFoundException.class, () -> service.assignAgent(uppercase,
                new AssignTicketRequest(AGENT_ID, 4L)));

        verify(tickets).findById(uppercase);
        verifyNoMoreInteractions(tickets);
        verifyNoInteractions(agents);
    }

    @Test
    void shouldGenerateLowercaseUuidStringsWithoutAnApplicationContext() {
        String generated = new ApplicationConfig().idGenerator().get();

        assertEquals(generated, UUID.fromString(generated).toString());
        assertEquals(generated, generated.toLowerCase(Locale.ROOT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingComments", "nullComment", "missingCommentTimestamp",
            "createdAt", "updatedAt", "status", "priority", "title", "description",
            "requesterName", "id"})
    void shouldRejectPredictableMappingIntegrityFailuresBeforeMutationOrSaving(String corruption) {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, OTHER_AGENT_ID);
        if (corruption.equals("missingComments")) {
            ReflectionTestUtils.setField(ticket, "comments", null);
        } else if (corruption.equals("nullComment")) {
            ReflectionTestUtils.setField(ticket, "comments", Arrays.asList((Comment) null));
        } else if (corruption.equals("missingCommentTimestamp")) {
            Comment comment = mock(Comment.class);
            when(comment.id()).thenReturn("00000000-0000-4000-8000-000000000001");
            when(comment.authorName()).thenReturn("Author");
            when(comment.content()).thenReturn("Content");
            ReflectionTestUtils.setField(ticket, "comments", List.of(comment));
        } else {
            ReflectionTestUtils.setField(ticket, corruption, null);
        }
        Object originalComments = ReflectionTestUtils.getField(ticket, "comments");
        Object originalUpdatedAt = ReflectionTestUtils.getField(ticket, "updatedAt");
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThrows(TicketIntegrityException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L)));

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(originalUpdatedAt, ticket.getUpdatedAt());
        assertEquals(4L, ticket.getVersion());
        assertSame(originalComments, ReflectionTestUtils.getField(ticket, "comments"));
        verify(tickets).findById(TICKET_ID);
        verifyNoMoreInteractions(tickets);
        verifyNoInteractions(agents);
    }

    @Test
    void shouldRejectMismatchedAgentLookupBeforeMutationOrSaving() {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, null);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(agents.findById(AGENT_ID)).thenReturn(Optional.of(
                new Agent(OTHER_AGENT_ID, "Different Agent", "agent@example.com")));

        assertThrows(TicketIntegrityException.class, () -> service.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L)));

        assertNull(ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void shouldRejectMissingBackendTimeBeforeMutationOrSaving() {
        Clock clock = mock(Clock.class);
        TicketService brokenClockService = new TicketService(tickets, agents, clock, () -> TICKET_ID);
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, null);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(agents.findById(AGENT_ID)).thenReturn(Optional.of(
                new Agent(AGENT_ID, "Agent", "agent@example.com")));

        assertThrows(TicketIntegrityException.class, () -> brokenClockService.assignAgent(TICKET_ID,
                new AssignTicketRequest(AGENT_ID, 4L)));

        assertNull(ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any(Ticket.class));
    }
}
