
package com.ticketflow.ticket;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<TicketSummaryResponse>> listTickets() {
        return ResponseEntity.ok(
                ticketService.listTickets());
    }

    @PatchMapping("/{id}/assignment")
    public ResponseEntity<TicketDetailResponse> assignAgent(
            @PathVariable String id,
            @Valid @RequestBody AssignTicketRequest request
    ) {
        return ResponseEntity.ok(ticketService.assignAgent(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketDetailResponse> changeStatus(
            @PathVariable String id,
            @Valid @RequestBody ChangeTicketStatusRequest request
    ) {
        return ResponseEntity.ok(ticketService.changeStatus(id, request));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentCreatedResponse> addComment(
            @PathVariable String id,
            @Valid @RequestBody AddCommentRequest request
    ) {
        return ResponseEntity.status(201).body(ticketService.addComment(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailResponse> getTicketById(
            @PathVariable String id
    ) {
        return ResponseEntity.ok(
                ticketService.getTicketById(id));
    }
}
