
package com.ticketflow.ticket;

import org.springframework.data.annotation.Version;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Document(collection = "tickets")
public class Ticket {

    @MongoId(FieldType.STRING)
    private String id;

    private String title;
    private String description;
    private String requesterName;
    private Priority priority;
    private TicketStatus status;
    private String assignedAgentId;
    private List<Comment> comments;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    @PersistenceCreator
    private Ticket() {
    }

    public Ticket(
            String id,
            String title,
            String description,
            String requesterName,
            Priority priority,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.title = validate(title, "Title", 120);
        this.description = validate(
                description, "Description", 2000);
        this.requesterName = validate(
                requesterName, "Requester name", 100);

        this.priority = Objects.requireNonNull(priority);
        this.status = TicketStatus.OPEN;
        this.assignedAgentId = null;
        this.comments = new ArrayList<>();
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    private static String validate(
            String value, String field, int maxLength
    ) {
        if (value == null || value.strip().isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required");
        }

        String trimmed = value.strip();

        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " cannot exceed "
                            + maxLength + " characters");
        }

        return trimmed;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public Priority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getAssignedAgentId() {
        return assignedAgentId;
    }

    public List<Comment> getComments() {
        return List.copyOf(comments);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
