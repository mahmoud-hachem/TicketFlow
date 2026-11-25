
package com.ticketflow.ticket;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketDetailResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {
        TicketDetailResponse response =
                ticketService.createTicket(request);

        URI location = URI.create(
                "/api/tickets/" + response.id());

        return ResponseEntity.created(location)
                .body(response);
    }
}
