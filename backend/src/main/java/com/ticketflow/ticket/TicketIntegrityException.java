package com.ticketflow.ticket;

public class TicketIntegrityException extends RuntimeException {
    public TicketIntegrityException(String message) {
        super(message);
    }
}
