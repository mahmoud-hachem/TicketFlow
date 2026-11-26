package com.ticketflow.ticket;

import com.ticketflow.agent.Agent;
import com.ticketflow.agent.AgentRepository;
import com.ticketflow.config.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.ticketflow.ticket.TicketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketStatusTest {
    private TicketRepository tickets;
    private AgentRepository agents;
    private TicketService service;
    private MockMvc mvc;
    private static final String PATH = "/api/tickets/" + TICKET_ID + "/status";

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        agents = mock(AgentRepository.class);
        service = new TicketService(tickets, agents, Clock.fixed(UPDATED, ZoneOffset.UTC), () -> TICKET_ID);
        mvc = MockMvcBuilders.standaloneSetup(new TicketController(service))
                .setControllerAdvice(new RestExceptionHandler()).build();
        when(agents.findById(AGENT_ID)).thenReturn(Optional.of(new Agent(AGENT_ID, "Agent", "agent@example.com")));
    }

    static boolean allowed(TicketStatus from, TicketStatus to) {
        return from == TicketStatus.OPEN && to == TicketStatus.IN_PROGRESS
                || from == TicketStatus.IN_PROGRESS && to == TicketStatus.RESOLVED
                || from == TicketStatus.RESOLVED && (to == TicketStatus.CLOSED || to == TicketStatus.IN_PROGRESS);
    }

    static Stream<Arguments> transitions() {
        return Stream.of(TicketStatus.values()).flatMap(from -> Stream.of(TicketStatus.values())
                .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void domainEnforcesEveryTransitionAndPreservesUnrelatedState(TicketStatus from, TicketStatus to) {
        Ticket ticket = ticket(from, 4L, AGENT_ID);
        comments(ticket);
        List<Comment> before = ticket.getComments();
        if (allowed(from, to)) {
            ticket.changeStatus(to, UPDATED);
            assertEquals(to, ticket.getStatus());
            assertEquals(UPDATED, ticket.getUpdatedAt());
        } else {
            Class<? extends RuntimeException> error = from == TicketStatus.CLOSED
                    ? TicketClosedException.class : InvalidTransitionException.class;
            assertThrows(error, () -> ticket.changeStatus(to, UPDATED));
            assertEquals(from, ticket.getStatus());
            assertEquals(CREATED, ticket.getUpdatedAt());
        }
        assertEquals(4L, ticket.getVersion());
        assertEquals(AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(Priority.HIGH, ticket.getPriority());
        assertEquals(CREATED, ticket.getCreatedAt());
        assertEquals(before, ticket.getComments());
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void apiEnforcesEveryTransitionWithoutSavingRejectedOperations(TicketStatus from, TicketStatus to)
            throws Exception {
        Ticket ticket = ticket(from, 4L, AGENT_ID);
        comments(ticket);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(tickets.save(ticket)).thenAnswer(call -> {
            ReflectionTestUtils.setField(ticket, "version", 5L);
            return ticket;
        });
        var result = mvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + to + "\",\"expectedVersion\":4}"));
        if (allowed(from, to)) {
            result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(to.name()))
                    .andExpect(jsonPath("$.version").value(5))
                    .andExpect(jsonPath("$.assignedAgent.id").value(AGENT_ID))
                    .andExpect(jsonPath("$.comments[0].content").value("First tied comment"));
            verify(tickets).save(ticket);
        } else {
            result.andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value(from == TicketStatus.CLOSED ? "TICKET_CLOSED" : "INVALID_TRANSITION"));
            verify(tickets, never()).save(any());
            assertEquals(from, ticket.getStatus());
            assertEquals(CREATED, ticket.getUpdatedAt());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "RESOLVED"})
    void missingAssigneeIsRejectedByDomainAndApi(String from) throws Exception {
        Ticket ticket = ticket(TicketStatus.valueOf(from), 4L, null);
        assertThrows(AssigneeRequiredException.class, () -> ticket.changeStatus(TicketStatus.IN_PROGRESS, UPDATED));
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        mvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"expectedVersion\":4}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ASSIGNEE_REQUIRED"));
        assertEquals(TicketStatus.valueOf(from), ticket.getStatus());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any());
    }

    static Stream<String> invalidJson() {
        return Stream.concat(Stream.of("null", "0", "true", "{}", "[]", "\"open\"", "\"UNKNOWN\"", "\"\"")
                        .map(value -> "{\"status\":" + value + ",\"expectedVersion\":4}"),
                Stream.concat(Stream.of("null", "-1", "4.5", "4.0", "\"4\"", "true", "[]", "{}", "9223372036854775808")
                        .map(value -> "{\"status\":\"RESOLVED\",\"expectedVersion\":" + value + "}"),
                        Stream.of("{}", "{", "null", "{\"status\":\"RESOLVED\"}", "{\"expectedVersion\":4}")));
    }

    @ParameterizedTest
    @MethodSource("invalidJson")
    void strictInputValidationRunsBeforeRepositoryAccess(String json) throws Exception {
        mvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(tickets, agents);
    }

    @Test
    void staleVersionIsRejectedBeforeMutationOrAgentLookup() throws Exception {
        Ticket ticket = ticket(TicketStatus.OPEN, 5L, AGENT_ID);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        mvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"expectedVersion\":4}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("VERSION_CONFLICT"));
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any());
        verifyNoInteractions(agents);
    }

    @Test
    void optimisticLockFailureIsMappedWithoutRetry() throws Exception {
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS, 4L, AGENT_ID);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(tickets.save(ticket)).thenThrow(new OptimisticLockingFailureException("internal"));
        mvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"expectedVersion\":4}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("VERSION_CONFLICT"));
        verify(tickets).save(ticket);
        verify(tickets).findById(TICKET_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "agent", "comments"})
    void corruptDataIsRejectedBeforePersistence(String corruption) throws Exception {
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS, 4L, AGENT_ID);
        if (corruption.equals("version")) ReflectionTestUtils.setField(ticket, "version", null);
        if (corruption.equals("agent")) when(agents.findById(AGENT_ID)).thenReturn(Optional.empty());
        if (corruption.equals("comments")) ReflectionTestUtils.setField(ticket, "comments", null);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        mvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"expectedVersion\":4}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any());
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        mvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"expectedVersion\":0}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("TICKET_NOT_FOUND"));
        verify(tickets, never()).save(any());
    }

    @Test
    void directInvalidArgumentsAndDomainInputsDoNotMutateOrLookup() {
        assertThrows(InvalidTicketInputException.class, () -> service.changeStatus(TICKET_ID, null));
        assertThrows(InvalidTicketInputException.class, () -> service.changeStatus("invalid", new ChangeTicketStatusRequest(TicketStatus.RESOLVED, 4L)));
        assertThrows(InvalidTicketInputException.class, () -> service.changeStatus(TICKET_ID, new ChangeTicketStatusRequest(null, 4L)));
        assertThrows(InvalidTicketInputException.class, () -> service.changeStatus(TICKET_ID, new ChangeTicketStatusRequest(TicketStatus.RESOLVED, null)));
        assertThrows(InvalidTicketInputException.class, () -> service.changeStatus(TICKET_ID, new ChangeTicketStatusRequest(TicketStatus.RESOLVED, -1L)));
        verifyNoInteractions(tickets, agents);
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, AGENT_ID);
        assertThrows(InvalidTicketInputException.class, () -> ticket.changeStatus(null, UPDATED));
        assertThrows(InvalidTicketInputException.class, () -> ticket.changeStatus(TicketStatus.IN_PROGRESS, null));
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertEquals(CREATED, ticket.getUpdatedAt());
    }

    @Test
    void returnsActualPersistenceVersionIncludingZeroPrecondition() {
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS, 0L, AGENT_ID);
        Ticket saved = ticket(TicketStatus.RESOLVED, 12L, AGENT_ID);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(tickets.save(ticket)).thenReturn(saved);
        assertEquals(12L, service.changeStatus(TICKET_ID, new ChangeTicketStatusRequest(TicketStatus.RESOLVED, 0L)).version());
        assertEquals(0L, ticket.getVersion());
    }
}
