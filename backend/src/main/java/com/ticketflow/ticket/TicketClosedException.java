package com.ticketflow.ticket;

public class TicketClosedException extends RuntimeException {
    public TicketClosedException() {
        super("Closed tickets cannot be assigned or reassigned.");
    }
}
