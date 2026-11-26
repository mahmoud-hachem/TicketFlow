
package com.ticketflow.ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ticketflow.agent.AgentRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketServiceTest {

    private TicketRepository repository;
    private TicketService service;

    private final Instant now =
            Instant.parse("2026-11-25T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(TicketRepository.class);

        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        service = new TicketService(
                repository,
                mock(AgentRepository.class),
                clock,
                () -> "test-ticket-id"
        );
    }

    @Test
    void shouldCreateTicketWithOpenStatus() {

        when(repository.save(any(Ticket.class)))
                .thenAnswer(invocation -> {
                    Ticket ticket = invocation.getArgument(0);
                    ReflectionTestUtils.setField(ticket, "version", 0L);
                    return ticket;
                });

        CreateTicketRequest request =
                new CreateTicketRequest(
                        "Printer problem",
                        "Printer is not responding",
                        "Test User",
                        Priority.HIGH
                );

        TicketDetailResponse result =
                service.createTicket(request);

        assertEquals("test-ticket-id", result.id());
        assertEquals(TicketStatus.OPEN, result.status());
        assertEquals(Priority.HIGH, result.priority());
        assertEquals(now, result.createdAt());
        assertEquals(now, result.updatedAt());
        assertNull(result.assignedAgent());
        assertTrue(result.comments().isEmpty());

        verify(repository).save(any(Ticket.class));
    }

    @Test
    void shouldRejectBlankTitleWithoutSaving() {

        CreateTicketRequest request =
                new CreateTicketRequest(
                        "   ",
                        "Printer problem",
                        "Test User",
                        Priority.HIGH
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createTicket(request)
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectTitleExceedingMaximumLength() {

        CreateTicketRequest request =
                new CreateTicketRequest(
                        "A".repeat(121),
                        "Printer problem",
                        "Test User",
                        Priority.HIGH
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createTicket(request)
        );

        verifyNoInteractions(repository);
    }
}
