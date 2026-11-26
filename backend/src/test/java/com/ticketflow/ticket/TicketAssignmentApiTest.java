package com.ticketflow.ticket;

import com.ticketflow.agent.Agent;
import com.ticketflow.agent.AgentRepository;
import com.ticketflow.config.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.ticketflow.ticket.TicketFixtures.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketAssignmentApiTest {

    private static final String PATH = "/api/tickets/" + TICKET_ID + "/assignment";

    private TicketRepository tickets;
    private AgentRepository agents;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        agents = mock(AgentRepository.class);
        when(agents.existsById(OTHER_AGENT_ID)).thenReturn(true);
        TicketService service = new TicketService(tickets, agents,
                Clock.fixed(UPDATED, ZoneOffset.UTC), () -> TICKET_ID);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketController(service))
                .setControllerAdvice(new RestExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private Ticket availableTicket(TicketStatus status, Long version, String agentId) {
        Ticket ticket = ticket(status, version, agentId);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        return ticket;
    }

    private void availableAgent() {
        when(agents.findById(AGENT_ID)).thenReturn(Optional.of(
                new Agent(AGENT_ID, "Demo Agent 1", "private@example.com")));
    }

    private ResultActions assign(long version) throws Exception {
        return mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + AGENT_ID + "\",\"expectedVersion\":" + version + "}"));
    }

    private ResultActions problem(ResultActions result, int status, String code, String path)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.errorCode").value(code));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {OTHER_AGENT_ID, AGENT_ID})
    void shouldAssignReassignAndAcceptSameAgentWithFullDetailResponse(String previousAgent)
            throws Exception {
        Ticket ticket = availableTicket(TicketStatus.OPEN, 4L, previousAgent);
        comments(ticket);
        List<Comment> before = ticket.getComments();
        availableAgent();
        when(tickets.save(ticket)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(ticket, "version", 5L);
            return ticket;
        });

        assign(4)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(TICKET_ID))
                .andExpect(jsonPath("$.title").value("Printer problem"))
                .andExpect(jsonPath("$.description").value("Printer is offline"))
                .andExpect(jsonPath("$.requesterName").value("Test User"))
                .andExpect(jsonPath("$.assignedAgent.id").value(AGENT_ID))
                .andExpect(jsonPath("$.assignedAgent.name").value("Demo Agent 1"))
                .andExpect(jsonPath("$.assignedAgent.email").doesNotExist())
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.createdAt").value(CREATED.toString()))
                .andExpect(jsonPath("$.updatedAt").value(UPDATED.toString()))
                .andExpect(jsonPath("$.comments.length()").value(3))
                .andExpect(jsonPath("$.comments[0].content").value("First tied comment"))
                .andExpect(jsonPath("$.comments[1].content").value("Second tied comment"))
                .andExpect(jsonPath("$.comments[2].content").value("Later comment"));

        assertEquals(before, ticket.getComments());
        verify(tickets).findById(TICKET_ID);
        if (OTHER_AGENT_ID.equals(previousAgent)) {
            verify(agents).existsById(OTHER_AGENT_ID);
        }
        verify(agents).findById(AGENT_ID);
        verify(tickets).save(ticket);
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldAcceptZeroExpectedVersionAndReturnRepositoryVersion() throws Exception {
        Ticket ticket = availableTicket(TicketStatus.OPEN, 0L, null);
        Ticket saved = ticket(TicketStatus.OPEN, 9L, AGENT_ID);
        saved.assignAgent(AGENT_ID, UPDATED);
        availableAgent();
        when(tickets.save(ticket)).thenReturn(saved);

        assign(0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(9));

        assertEquals(0L, ticket.getVersion());
        verify(tickets).save(ticket);
    }

    static Stream<String> invalidRequests() {
        Stream<String> versions = Stream.of("null", "-1", "4.5", "4.0", "4e0", "\"4\"",
                        "\"\"", "true", "[]", "{}", "9223372036854775808")
                .map(version -> "{\"agentId\":\"" + AGENT_ID + "\",\"expectedVersion\":" + version + "}");
        Stream<String> agentIds = Stream.of("null", "\"\"", "\" \"", "\"invalid\"",
                        "\"1-1-1-1-1\"", "42", "true", "[]", "{}")
                .map(agentId -> "{\"agentId\":" + agentId + ",\"expectedVersion\":4}");
        return Stream.concat(Stream.concat(versions, agentIds), Stream.of(
                "{}", "{", "[]", "null", "{\"expectedVersion\":4}",
                "{\"agentId\":\"" + AGENT_ID + "\"}"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void shouldRejectInvalidRequestsBeforeAnyRepositoryAccess(String json) throws Exception {
        problem(mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content(json)),
                400, "VALIDATION_ERROR", PATH);

        verifyNoInteractions(tickets, agents);
    }

    @Test
    void shouldReturnUsefulRequiredFieldErrors() throws Exception {
        problem(mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content("{}")),
                400, "VALIDATION_ERROR", PATH)
                .andExpect(jsonPath("$.fieldErrors.agentId").exists())
                .andExpect(jsonPath("$.fieldErrors.expectedVersion").exists());

        verifyNoInteractions(tickets, agents);
    }

    @Test
    void shouldReturnTicketNotFoundWithoutSaving() throws Exception {
        problem(assign(4), 404, "TICKET_NOT_FOUND", PATH)
                .andExpect(jsonPath("$.detail").value("Ticket not found"));

        verify(tickets).findById(TICKET_ID);
        verifyNoMoreInteractions(tickets);
        verifyNoInteractions(agents);
    }

    @Test
    void shouldReturnAgentNotFoundWithoutMutationOrSaving() throws Exception {
        Ticket ticket = availableTicket(TicketStatus.OPEN, 4L, OTHER_AGENT_ID);

        problem(assign(4), 404, "AGENT_NOT_FOUND", PATH);

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void shouldReturnClosedConflictWithoutMutationOrSaving() throws Exception {
        Ticket ticket = availableTicket(TicketStatus.CLOSED, 4L, OTHER_AGENT_ID);
        availableAgent();

        problem(assign(4), 409, "TICKET_CLOSED", PATH);

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void shouldReturnVersionConflictBeforeAgentLookupOrMutation() throws Exception {
        Ticket ticket = availableTicket(TicketStatus.OPEN, 5L, OTHER_AGENT_ID);

        problem(assign(4), 409, "VERSION_CONFLICT", PATH)
                .andExpect(jsonPath("$.detail").value(
                        "This ticket changed since you opened it. Reload and try again."));

        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any(Ticket.class));
        verifyNoInteractions(agents);
    }

    @Test
    void shouldMapOptimisticLockFailureToSafeConflictWithoutRetry() throws Exception {
        Ticket ticket = availableTicket(TicketStatus.OPEN, 4L, null);
        availableAgent();
        when(tickets.save(ticket)).thenThrow(new OptimisticLockingFailureException(
                "Internal persistence diagnostics must not be exposed"));

        problem(assign(4), 409, "VERSION_CONFLICT", PATH)
                .andExpect(content().string(not(containsString("Internal persistence"))));

        verify(tickets).findById(TICKET_ID);
        verify(agents).findById(AGENT_ID);
        verify(tickets).save(ticket);
        verifyNoMoreInteractions(tickets, agents);
    }

    @Test
    void shouldRejectNullStoredVersionAsSafeIntegrityErrorWithoutSaving() throws Exception {
        Ticket ticket = availableTicket(TicketStatus.OPEN, null, OTHER_AGENT_ID);

        problem(assign(0), 500, "INTERNAL_ERROR", PATH)
                .andExpect(jsonPath("$.detail").value("Stored ticket data is inconsistent."))
                .andExpect(content().string(not(containsString("persisted version"))));

        assertNull(ticket.getVersion());
        assertEquals(OTHER_AGENT_ID, ticket.getAssignedAgentId());
        verify(tickets, never()).save(any(Ticket.class));
        verifyNoInteractions(agents);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReturnSafeIntegrityErrorForDanglingListAndDetailReferences(boolean list)
            throws Exception {
        Ticket ticket = availableTicket(TicketStatus.OPEN, 4L, AGENT_ID);
        when(tickets.findAllByOrderByCreatedAtDescIdAsc()).thenReturn(List.of(ticket));
        String path = list ? "/api/tickets" : "/api/tickets/" + TICKET_ID;

        problem(mockMvc.perform(get(path)), 500, "INTERNAL_ERROR", path)
                .andExpect(jsonPath("$.detail").value("Stored ticket data is inconsistent."))
                .andExpect(content().string(not(containsString(AGENT_ID))));

        assertEquals(AGENT_ID, ticket.getAssignedAgentId());
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void shouldMapUnexpectedFailureToGenericProblemWithoutInternalDetails() throws Exception {
        when(tickets.findById(TICKET_ID)).thenThrow(new DataAccessResourceFailureException(
                "Sensitive internal diagnostics"));

        problem(assign(4), 500, "INTERNAL_ERROR", PATH)
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("Sensitive"))))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());

        verify(tickets, never()).save(any(Ticket.class));
        verifyNoInteractions(agents);
    }

    @Test
    void shouldPreserveFrameworkMethodNotAllowedStatusAndHeaders() throws Exception {
        mockMvc.perform(post(PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("PATCH")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.instance").value(PATH));

        verifyNoInteractions(tickets, agents);
    }

    @Test
    void shouldMapAssignedAndUnassignedTicketsInReadApisWithoutEmail() throws Exception {
        Ticket ticket = availableTicket(TicketStatus.RESOLVED, 4L, AGENT_ID);
        comments(ticket);
        Ticket unassigned = ticket(TicketStatus.OPEN, 0L, null);
        ReflectionTestUtils.setField(unassigned, "id", "unassigned-ticket");
        when(tickets.findAllByOrderByCreatedAtDescIdAsc()).thenReturn(List.of(ticket, unassigned));
        Agent agent = new Agent(AGENT_ID, "Demo Agent 1", "private@example.com");
        when(agents.findById(AGENT_ID)).thenReturn(Optional.of(agent));
        when(agents.findAllById(any())).thenReturn(List.of(agent));

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignedAgent.id").value(AGENT_ID))
                .andExpect(jsonPath("$[0].assignedAgent.name").value("Demo Agent 1"))
                .andExpect(jsonPath("$[0].assignedAgent.email").doesNotExist())
                .andExpect(jsonPath("$[1].assignedAgent").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/tickets/" + TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAgent.id").value(AGENT_ID))
                .andExpect(jsonPath("$.assignedAgent.email").doesNotExist())
                .andExpect(jsonPath("$.comments[0].content").value("First tied comment"));

        verify(tickets, never()).save(any(Ticket.class));
    }
}
