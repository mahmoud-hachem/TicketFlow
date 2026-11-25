
package com.ticketflow.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 120, message = "Title cannot exceed 120 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        @NotBlank(message = "Requester name is required")
        @Size(max = 100, message = "Requester name cannot exceed 100 characters")
        String requesterName,

        @NotNull(message = "Priority is required")
        Priority priority

) {
    public CreateTicketRequest {
        title = trim(title);
        description = trim(description);
        requesterName = trim(requesterName);
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
