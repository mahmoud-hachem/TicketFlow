package com.ticketflow.ticket;

public class InvalidTicketInputException extends IllegalArgumentException {
    public InvalidTicketInputException(String message) {
        super(message);
    }
}
