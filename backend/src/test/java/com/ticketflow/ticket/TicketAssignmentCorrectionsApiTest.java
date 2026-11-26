package com.ticketflow.ticket;

import com.ticketflow.agent.Agent;
import com.ticketflow.agent.AgentRepository;
import com.ticketflow.config.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

import static com.ticketflow.ticket.TicketFixtures.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketAssignmentCorrectionsApiTest {

    private static final String PATH = "/api/tickets/" + TICKET_ID + "/assignment";
    private TicketRepository tickets;
    private AgentRepository agents;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        agents = mock(AgentRepository.class);
        TicketService service = new TicketService(tickets, agents,
                Clock.fixed(UPDATED, ZoneOffset.UTC), () -> TICKET_ID);
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketController(service))
                .setControllerAdvice(new RestExceptionHandler()).build();
    }

    private ResultActions assign(String path, String agentId) throws Exception {
        return mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentId\":\"" + agentId + "\",\"expectedVersion\":4}"));
    }

    private void expectIntegrityFailure(ResultActions result) throws Exception {
        result.andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Stored ticket data is inconsistent."))
                .andExpect(jsonPath("$.instance").value(PATH))
                .andExpect(content().string(not(containsString(OTHER_AGENT_ID))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReturnSafe500ForDanglingExistingAssignmentWithoutSaving(boolean sameAgent)
            throws Exception {
        String currentAgentId = sameAgent ? AGENT_ID : OTHER_AGENT_ID;
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, currentAgentId);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        if (!sameAgent) {
            when(agents.findById(AGENT_ID)).thenReturn(Optional.of(
                    new Agent(AGENT_ID, "Target Agent", "agent@example.com")));
        }

        expectIntegrityFailure(assign(PATH, AGENT_ID));

        assertEquals(currentAgentId, ticket.getAssignedAgentId());
        assertEquals(CREATED, ticket.getUpdatedAt());
        assertEquals(4L, ticket.getVersion());
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void shouldReturnSafe500ForMissingCommentsBeforePersistence() throws Exception {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, null);
        ReflectionTestUtils.setField(ticket, "comments", null);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        expectIntegrityFailure(assign(PATH, AGENT_ID));

        assertNull(ticket.getAssignedAgentId());
        assertNull(ReflectionTestUtils.getField(ticket, "comments"));
        assertEquals(CREATED, ticket.getUpdatedAt());
        verify(tickets, never()).save(any(Ticket.class));
        verifyNoInteractions(agents);
    }

    @Test
    void shouldTreatInternalIllegalArgumentAsSafe500() throws Exception {
        when(tickets.findById(TICKET_ID)).thenThrow(
                new IllegalArgumentException("SENSITIVE_INTERNAL_MARKER"));

        assign(PATH, AGENT_ID)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("SENSITIVE_INTERNAL_MARKER"))));

        verify(tickets, never()).save(any(Ticket.class));
        verifyNoInteractions(agents);
    }

    @Test
    void shouldKeepUppercaseTargetAsAnExactLookupRatherThanNormalizeIt() throws Exception {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, null);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(agents.findById(AGENT_ID)).thenReturn(Optional.of(
                new Agent(AGENT_ID, "Lowercase Agent", "agent@example.com")));
        String uppercase = AGENT_ID.toUpperCase(Locale.ROOT);

        assign(PATH, uppercase)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AGENT_NOT_FOUND"));

        verify(agents).findById(uppercase);
        verifyNoMoreInteractions(agents);
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void shouldRejectInvalidAssignmentPathBeforeRepositoryInteraction() throws Exception {
        assign("/api/tickets/not-a-uuid/assignment", AGENT_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Invalid input"));

        verifyNoInteractions(tickets, agents);
    }
}
