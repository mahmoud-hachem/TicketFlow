
package com.ticketflow.ticket;

import com.ticketflow.agent.AgentSummaryResponse;
import java.time.Instant;
import java.util.List;

public record TicketDetailResponse(
        String id,
        String title,
        String description,
        String requesterName,
        Priority priority,
        TicketStatus status,
        AgentSummaryResponse assignedAgent,
        List<CommentResponse> comments,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
