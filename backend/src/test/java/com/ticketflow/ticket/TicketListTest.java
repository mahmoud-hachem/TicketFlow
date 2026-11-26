
package com.ticketflow.ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ticketflow.agent.AgentRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketListTest {

    private TicketRepository ticketRepository;
    private TicketService ticketService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);

        ticketService = new TicketService(
                ticketRepository,
                mock(AgentRepository.class),
                Clock.systemUTC(),
                () -> "test-id"
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new TicketController(ticketService))
                .build();
    }

    private Ticket createTicket(
            String id,
            String title,
            Instant createdAt
    ) {
        Ticket ticket = new Ticket(
                id,
                title,
                "Test description",
                "Test User",
                Priority.HIGH,
                createdAt
        );
        ReflectionTestUtils.setField(ticket, "version", 0L);
        return ticket;
    }

    @Test
    void shouldReturnAllTickets() throws Exception {

        Ticket ticket = createTicket(
                "ticket-1",
                "Printer problem",
                Instant.parse("2026-11-25T10:00:00Z")
        );

        when(ticketRepository
                .findAllByOrderByCreatedAtDescIdAsc())
                .thenReturn(List.of(ticket));

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("ticket-1"))
                .andExpect(jsonPath("$[0].title").value("Printer problem"))
                .andExpect(jsonPath("$[0].requesterName").value("Test User"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists());

        verify(ticketRepository)
                .findAllByOrderByCreatedAtDescIdAsc();
    }

    @Test
    void shouldReturnEmptyList() throws Exception {

        when(ticketRepository
                .findAllByOrderByCreatedAtDescIdAsc())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldPreserveRepositoryOrdering() {

        Ticket newest = createTicket(
                "ticket-2",
                "Newest ticket",
                Instant.parse("2026-11-25T11:00:00Z")
        );

        Ticket oldest = createTicket(
                "ticket-1",
                "Oldest ticket",
                Instant.parse("2026-11-25T10:00:00Z")
        );

        when(ticketRepository
                .findAllByOrderByCreatedAtDescIdAsc())
                .thenReturn(List.of(newest, oldest));

        List<TicketSummaryResponse> result =
                ticketService.listTickets();

        assertEquals(2, result.size());
        assertEquals("ticket-2", result.get(0).id());
        assertEquals("ticket-1", result.get(1).id());
    }

    @Test
    void shouldReturnUnassignedTicket() {

        Ticket ticket = createTicket(
                "ticket-1",
                "Network problem",
                Instant.parse("2026-11-25T10:00:00Z")
        );

        when(ticketRepository
                .findAllByOrderByCreatedAtDescIdAsc())
                .thenReturn(List.of(ticket));

        List<TicketSummaryResponse> result =
                ticketService.listTickets();

        assertNull(result.get(0).assignedAgent());
        assertEquals(TicketStatus.OPEN, result.get(0).status());
        assertEquals(Priority.HIGH, result.get(0).priority());
    }
}
