package com.ticketflow.ticket;

import java.time.Instant;

public record CommentCreatedResponse(
        String ticketId, CommentResponse comment, Long ticketVersion, Instant updatedAt
) {
}
