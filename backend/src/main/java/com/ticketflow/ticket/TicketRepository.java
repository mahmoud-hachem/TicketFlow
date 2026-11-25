
package com.ticketflow.ticket;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface TicketRepository
        extends MongoRepository<Ticket, String> {

    List<Ticket> findAllByOrderByCreatedAtDescIdAsc();
}
