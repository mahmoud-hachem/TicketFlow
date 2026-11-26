package com.ticketflow.agent;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AgentService {

    private final AgentRepository agentRepository;

    public AgentService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public List<AgentSummaryResponse> listAgents() {
        return agentRepository.findAll().stream()
                .sorted(Comparator.comparing(Agent::getName)
                        .thenComparing(Agent::getId))
                .map(agent -> new AgentSummaryResponse(
                        agent.getId(), agent.getName()))
                .toList();
    }
}
