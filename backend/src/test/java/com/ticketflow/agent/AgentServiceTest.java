package com.ticketflow.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    private AgentRepository repository;
    private AgentService service;

    @BeforeEach
    void setUp() {
        repository = mock(AgentRepository.class);
        service = new AgentService(repository);
    }

    @Test
    void shouldRetrieveAndMapStoredAgents() {
        Agent agent = new Agent("d50c63a2-85af-424f-9c6e-dc90f7f303f9",
                "Available Agent", "private@example.com");
        when(repository.findAll()).thenReturn(List.of(agent));

        assertEquals(List.of(new AgentSummaryResponse(agent.getId(), "Available Agent")),
                service.listAgents());
        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldSortByNameThenIdRegardlessOfRepositoryOrder() {
        Agent last = new Agent("06e099e6-9550-4e33-91a0-64d811250005",
                "Zoe", "zoe@example.com");
        Agent second = new Agent("d50c63a2-85af-424f-9c6e-dc90f7f303f9",
                "Alex", "alex2@example.com");
        Agent first = new Agent("123c20bb-0cc9-4d62-8cc5-2bb7e05e5664",
                "Alex", "alex1@example.com");
        when(repository.findAll()).thenReturn(List.of(last, second, first));

        assertEquals(List.of(
                new AgentSummaryResponse(first.getId(), "Alex"),
                new AgentSummaryResponse(second.getId(), "Alex"),
                new AgentSummaryResponse(last.getId(), "Zoe")
        ), service.listAgents());
    }

    @Test
    void shouldReturnEmptyListWhenNoAgentsExist() {
        when(repository.findAll()).thenReturn(List.of());

        assertTrue(service.listAgents().isEmpty());
        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
    }
}
