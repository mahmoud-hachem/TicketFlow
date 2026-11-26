package com.ticketflow.agent;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        name = "ticketflow.demo-seeding.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class DemoAgentSeeder implements ApplicationRunner {

    private static final List<Agent> DEMO_AGENTS = List.of(
            new Agent("d50c63a2-85af-424f-9c6e-dc90f7f303f9",
                    "Demo Agent 1", "agent1@example.com"),
            new Agent("123c20bb-0cc9-4d62-8cc5-2bb7e05e5664",
                    "Demo Agent 2", "agent2@example.com"),
            new Agent("06e099e6-9550-4e33-91a0-64d811250005",
                    "Demo Agent 3", "agent3@example.com")
    );

    private final AgentRepository agentRepository;

    public DemoAgentSeeder(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Agent agent : DEMO_AGENTS) {
            if (agentRepository.existsById(agent.getId())) {
                continue;
            }

            try {
                agentRepository.insert(agent);
            } catch (DuplicateKeyException ex) {
                // A competing initializer is harmless only if this ID now exists.
                if (!agentRepository.existsById(agent.getId())) {
                    throw ex;
                }
            }
        }
    }
}
