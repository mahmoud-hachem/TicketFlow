package com.ticketflow.agent;

public class AgentNotFoundException extends RuntimeException {
    public AgentNotFoundException() {
        super("Agent not found");
    }
}
