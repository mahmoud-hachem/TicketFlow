package com.ticketflow.ticket;

public class TicketVersionConflictException extends RuntimeException {
    public TicketVersionConflictException() {
        super("This ticket changed since you opened it. Reload and try again.");
    }
}
