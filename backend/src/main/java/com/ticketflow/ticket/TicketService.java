
package com.ticketflow.ticket;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

        return toDetailResponse(saved);
    }

    public List<TicketSummaryResponse> listTickets() {
        return ticketRepository
                .findAllByOrderByCreatedAtDescIdAsc()
                .stream()
                .map(ticket -> new TicketSummaryResponse(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getRequesterName(),
                        ticket.getStatus(),
                        ticket.getPriority(),
                        null,
                        ticket.getCreatedAt(),
                        ticket.getUpdatedAt(),
                        ticket.getVersion()
                ))
                .toList();
    }

    public TicketDetailResponse getTicketById(String id) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket not found"
                ));

        return toDetailResponse(ticket);
    }

    private TicketDetailResponse toDetailResponse(Ticket ticket) {
        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getRequesterName(),
                ticket.getPriority(),
                ticket.getStatus(),
                null,
                List.of(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getVersion()
        );
    }
}
