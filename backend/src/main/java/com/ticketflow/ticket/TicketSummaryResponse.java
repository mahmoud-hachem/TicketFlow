
package com.ticketflow.ticket;

import com.ticketflow.agent.AgentSummaryResponse;
import java.time.Instant;

public record TicketSummaryResponse(
        String id,
        String title,
        String requesterName,
        TicketStatus status,
        Priority priority,
        AgentSummaryResponse assignedAgent,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
