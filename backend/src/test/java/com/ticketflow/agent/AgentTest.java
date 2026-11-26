package com.ticketflow.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    private static final String ID = "d50c63a2-85af-424f-9c6e-dc90f7f303f9";

    @Test
    void shouldCreateAgentWithTrimmedText() {
        Agent agent = new Agent(ID, "  Demo Agent 1  ", "  agent1@example.com  ");

        assertEquals(ID, agent.getId());
        assertEquals("Demo Agent 1", agent.getName());
        assertEquals("agent1@example.com", agent.getEmail());
    }

    @Test
    void shouldRejectInvalidUuidStrings() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent(null, "Agent", "agent@example.com")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent("", "Agent", "agent@example.com")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent("not-a-uuid", "Agent", "agent@example.com")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent("1-1-1-1-1", "Agent", "agent@example.com"))
        );
    }

    @Test
    void shouldRejectMissingName() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent(ID, null, "agent@example.com")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent(ID, " \t\n ", "agent@example.com"))
        );
    }

    @Test
    void shouldRejectMissingOrMalformedEmail() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent(ID, "Agent", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent(ID, "Agent", "  ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent(ID, "Agent", "invalid")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent(ID, "Agent", "a@@example.com")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Agent(ID, "Agent", "a b@example.com"))
        );
    }
}
