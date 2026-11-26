package com.ticketflow.agent;

import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.UUID;

@Document(collection = "agents")
public class Agent {

    @MongoId(FieldType.STRING)
    private final String id;

    private final String name;
    private final String email;

    @PersistenceCreator
    public Agent(String id, String name, String email) {
        if (id == null || !UUID.fromString(id).toString().equalsIgnoreCase(id)) {
            throw new IllegalArgumentException("Agent ID must be a UUID string");
        }

        this.id = id;
        this.name = requireText(name, "Agent name");
        this.email = requireText(email, "Agent email");

        if (!this.email.matches("[^\\s@]+@[^\\s@]+")) {
            throw new IllegalArgumentException("Agent email must have a valid format");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.strip().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value.strip();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
