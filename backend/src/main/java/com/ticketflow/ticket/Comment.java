
package com.ticketflow.ticket;

import java.time.Instant;
import java.util.Objects;

public record Comment(
        String id,
        String authorName,
        String content,
        Instant createdAt
) {
    public Comment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(createdAt);

        authorName = validate(authorName, 100);
        content = validate(content, 2000);
    }

    static String validate(String value, int maxLength) {
        if (value == null || value.strip().isBlank()) {
            throw new InvalidTicketInputException(
                    "Comment fields must not be blank");
        }

        String trimmed = value.strip();

        if (trimmed.length() > maxLength) {
            throw new InvalidTicketInputException(
                    "Maximum length exceeded: " + maxLength);
        }

        return trimmed;
    }
}
