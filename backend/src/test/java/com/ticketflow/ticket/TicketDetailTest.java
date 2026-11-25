
package com.ticketflow.ticket;

import com.ticketflow.config.RestExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketDetailTest {

    private TicketRepository ticketRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        ticketRepository = mock(TicketRepository.class);

        TicketService ticketService = new TicketService(
                ticketRepository,
                Clock.systemUTC(),
                () -> "test-id"
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new TicketController(ticketService))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    private Ticket createTestTicket() {

        return new Ticket(
                "ticket-1",
                "Printer not working",
                "The office printer is not responding.",
                "Test User",
                Priority.MEDIUM,
                Instant.parse("2026-11-25T10:00:00Z")
        );
    }

    @Test
    void shouldReturnTicketDetails() throws Exception {

        when(ticketRepository.findById("ticket-1"))
                .thenReturn(Optional.of(createTestTicket()));

        mockMvc.perform(get("/api/tickets/ticket-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ticket-1"))
                .andExpect(jsonPath("$.title")
                        .value("Printer not working"))
                .andExpect(jsonPath("$.description")
                        .value("The office printer is not responding."))
                .andExpect(jsonPath("$.requesterName")
                        .value("Test User"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        verify(ticketRepository).findById("ticket-1");
    }

    @Test
    void shouldReturnNotFoundForUnknownTicket() throws Exception {

        when(ticketRepository.findById("unknown-ticket"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tickets/unknown-ticket"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode")
                        .value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.detail")
                        .value("Ticket not found"))
                .andExpect(jsonPath("$.type")
                        .value("about:blank"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/tickets/unknown-ticket"));
    }

    @Test
    void shouldReturnEmptyComments() throws Exception {

        when(ticketRepository.findById("ticket-1"))
                .thenReturn(Optional.of(createTestTicket()));

        mockMvc.perform(get("/api/tickets/ticket-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments").isArray())
                .andExpect(jsonPath("$.comments").isEmpty());
    }

    @Test
    void shouldReturnUnassignedAgent() throws Exception {

        when(ticketRepository.findById("ticket-1"))
                .thenReturn(Optional.of(createTestTicket()));

        mockMvc.perform(get("/api/tickets/ticket-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAgent").doesNotExist());
    }
}
