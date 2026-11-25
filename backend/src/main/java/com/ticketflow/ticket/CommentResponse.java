package com.ticketflow.ticket;

import java.time.Instant;

public record CommentResponse(
        String id,
        String authorName,
        String content,
        Instant createdAt
) {
}