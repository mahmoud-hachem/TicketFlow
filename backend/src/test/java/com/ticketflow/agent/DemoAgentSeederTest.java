package com.ticketflow.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoAgentSeederTest {

    private static final String FIRST_ID = "d50c63a2-85af-424f-9c6e-dc90f7f303f9";
    private static final String SECOND_ID = "123c20bb-0cc9-4d62-8cc5-2bb7e05e5664";
    private static final String THIRD_ID = "06e099e6-9550-4e33-91a0-64d811250005";

    private AgentRepository repository;
    private DemoAgentSeeder seeder;

    @BeforeEach
    void setUp() {
        repository = mock(AgentRepository.class);
        seeder = new DemoAgentSeeder(repository);
    }

    private void runSeeder() {
        seeder.run(new DefaultApplicationArguments(new String[0]));
    }

    @Test
    void shouldInsertTheThreeApprovedAgentsWhenMissing() {
        runSeeder();

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(repository, times(3)).insert(captor.capture());
        List<Agent> agents = captor.getAllValues();
        assertEquals(List.of(FIRST_ID, SECOND_ID, THIRD_ID),
                agents.stream().map(Agent::getId).toList());
        assertEquals(List.of("Demo Agent 1", "Demo Agent 2", "Demo Agent 3"),
                agents.stream().map(Agent::getName).toList());
        assertEquals(List.of("agent1@example.com", "agent2@example.com", "agent3@example.com"),
                agents.stream().map(Agent::getEmail).toList());
        verify(repository).existsById(FIRST_ID);
        verify(repository).existsById(SECOND_ID);
        verify(repository).existsById(THIRD_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldLeaveExistingAgentsUnchanged() {
        when(repository.existsById(anyString())).thenReturn(true);

        runSeeder();

        verify(repository).existsById(FIRST_ID);
        verify(repository).existsById(SECOND_ID);
        verify(repository).existsById(THIRD_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldNotDuplicateAgentsOnRepeatedExecution() {
        Map<String, Agent> stored = new HashMap<>();
        when(repository.existsById(anyString())).thenAnswer(
                invocation -> stored.containsKey(invocation.getArgument(0)));
        when(repository.insert(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            assertNull(stored.putIfAbsent(agent.getId(), agent));
            return agent;
        });

        runSeeder();
        runSeeder();

        assertEquals(3, stored.size());
        verify(repository, times(3)).insert(any(Agent.class));
        verify(repository, times(2)).existsById(FIRST_ID);
        verify(repository, times(2)).existsById(SECOND_ID);
        verify(repository, times(2)).existsById(THIRD_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldInsertOnlyMissingAgentsDuringPartialInitialization() {
        Agent edited = new Agent(SECOND_ID, "Edited display name", "edited@example.com");
        Map<String, Agent> stored = new HashMap<>();
        stored.put(SECOND_ID, edited);
        when(repository.existsById(anyString())).thenAnswer(
                invocation -> stored.containsKey(invocation.getArgument(0)));
        when(repository.insert(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            assertNull(stored.putIfAbsent(agent.getId(), agent));
            return agent;
        });

        runSeeder();

        assertEquals(3, stored.size());
        assertSame(edited, stored.get(SECOND_ID));
        assertEquals("Edited display name", stored.get(SECOND_ID).getName());
        assertEquals("edited@example.com", stored.get(SECOND_ID).getEmail());
        verify(repository, times(2)).insert(any(Agent.class));
        verify(repository).existsById(FIRST_ID);
        verify(repository).existsById(SECOND_ID);
        verify(repository).existsById(THIRD_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldAcceptConcurrentDuplicateOnlyWhenIntendedIdNowExists() {
        when(repository.existsById(FIRST_ID)).thenReturn(false, true);
        when(repository.existsById(SECOND_ID)).thenReturn(true);
        when(repository.existsById(THIRD_ID)).thenReturn(true);
        when(repository.insert(any(Agent.class)))
                .thenThrow(new DuplicateKeyException("Competing initializer"));

        assertDoesNotThrow(this::runSeeder);

        verify(repository, times(2)).existsById(FIRST_ID);
        verify(repository).existsById(SECOND_ID);
        verify(repository).existsById(THIRD_ID);
        verify(repository).insert(any(Agent.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldRethrowDuplicateWhenIntendedIdStillDoesNotExist() {
        DuplicateKeyException failure = new DuplicateKeyException("Unrelated duplicate");
        when(repository.insert(any(Agent.class))).thenThrow(failure);

        assertSame(failure, assertThrows(DuplicateKeyException.class, this::runSeeder));

        verify(repository, times(2)).existsById(FIRST_ID);
        verify(repository).insert(any(Agent.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldPropagateUnrelatedInsertFailures() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("Database unavailable");
        when(repository.insert(any(Agent.class))).thenThrow(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                this::runSeeder));

        verify(repository).existsById(FIRST_ID);
        verify(repository).insert(any(Agent.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldPropagateExistenceLookupFailures() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("Database unavailable");
        when(repository.existsById(FIRST_ID)).thenThrow(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                this::runSeeder));

        verify(repository).existsById(FIRST_ID);
        verifyNoMoreInteractions(repository);
    }
}
