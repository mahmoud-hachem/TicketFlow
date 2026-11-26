package com.ticketflow.ticket;

import com.ticketflow.agent.AgentRepository;
import com.ticketflow.config.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketAcceptanceCoverageTest {
    private TicketRepository tickets;
    private AgentRepository agents;
    private TicketService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class); agents = mock(AgentRepository.class);
        service = new TicketService(tickets, agents, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                () -> "b8f6ee9f-bf96-45d2-ac64-e6b1f208301a");
        mvc = MockMvcBuilders.standaloneSetup(new TicketController(service))
                .setControllerAdvice(new RestExceptionHandler()).build();
        when(tickets.save(any(Ticket.class))).thenAnswer(call -> {
            Ticket ticket = call.getArgument(0);
            ReflectionTestUtils.setField(ticket, "version", 0L); return ticket;
        });
    }

    @ParameterizedTest @EnumSource(Priority.class)
    void everyApprovedPriorityCreatesAnOpenUnassignedTicket(Priority priority) throws Exception {
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonMapper.builder().build().writeValueAsString(
                                new CreateTicketRequest("Title", "Description", "Requester", priority))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.priority").value(priority.name()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedAgent").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.comments").isEmpty()).andExpect(jsonPath("$.version").value(0));
        verify(tickets).save(any(Ticket.class));
    }

    @Test
    void exactTrimmedMaximumsAreAcceptedAtDtoAndDomainBoundaries() throws Exception {
        var request = new CreateTicketRequest("  " + "t".repeat(120) + "  ",
                "  " + "d".repeat(2000) + "  ", "  " + "r".repeat(100) + "  ", Priority.MEDIUM);
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  " + "t".repeat(120) + "  \",\"description\":\"  " + "d".repeat(2000)
                                + "  \",\"requesterName\":\"  " + "r".repeat(100) + "  \",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value(request.title()))
                .andExpect(jsonPath("$.description").value(request.description()))
                .andExpect(jsonPath("$.requesterName").value(request.requesterName()));
    }

    private CreateTicketRequest invalid(String field, boolean overLimit) {
        String title = "Title", description = "Description", requester = "Requester";
        if (field.equals("title")) title = overLimit ? "x".repeat(121) : " \t\n ";
        if (field.equals("description")) description = overLimit ? "x".repeat(2001) : " \t\n ";
        if (field.equals("requesterName")) requester = overLimit ? "x".repeat(101) : " \t\n ";
        return new CreateTicketRequest(title, description, requester, Priority.HIGH);
    }

    private void rejects(String field, boolean overLimit) throws Exception {
        var request = invalid(field, overLimit);
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(JsonMapper.builder().build().writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors." + field).exists());
        assertThrows(InvalidTicketInputException.class, () -> service.createTicket(request));
        verifyNoInteractions(tickets, agents);
    }

    @ParameterizedTest @ValueSource(strings = {"title", "description", "requesterName"})
    void blankTextIsRejectedWithoutPersistence(String field) throws Exception { rejects(field, false); }

    @ParameterizedTest @ValueSource(strings = {"title", "description", "requesterName"})
    void overLimitTextIsRejectedWithoutPersistence(String field) throws Exception { rejects(field, true); }
}
