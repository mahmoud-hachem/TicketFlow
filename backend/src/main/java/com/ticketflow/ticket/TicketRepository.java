package com.ticketflow.ticket;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TicketRepository
        extends MongoRepository<Ticket, String> {
}