
package com.ticketflow.ticket;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    public TicketService(
            TicketRepository ticketRepository,
            Clock clock,
            Supplier<String> idGenerator
    ) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public TicketDetailResponse createTicket(
            CreateTicketRequest request
    ) {
        Ticket ticket = new Ticket(
                idGenerator.get(),
                request.title(),
                request.description(),
                request.requesterName(),
                request.priority(),
                Instant.now(clock)
        );

        Ticket saved = ticketRepository.save(ticket);

        return new TicketDetailResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getRequesterName(),
                saved.getPriority(),
                saved.getStatus(),
                null,
                List.of(),
                saved.getCreatedAt(),
                saved.getUpdatedAt(),
                saved.getVersion()
        );
    }
}
