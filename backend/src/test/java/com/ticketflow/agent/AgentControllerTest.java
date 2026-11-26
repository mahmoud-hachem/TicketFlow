package com.ticketflow.agent;

import com.ticketflow.config.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

class AgentControllerTest {

    private AgentRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = mock(AgentRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentController(new AgentService(repository)))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnOnlySortedAgentIdsAndNames() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new Agent("d50c63a2-85af-424f-9c6e-dc90f7f303f9",
                        "Zoe", "zoe@example.com"),
                new Agent("123c20bb-0cc9-4d62-8cc5-2bb7e05e5664",
                        "Alex", "alex@example.com")
        ));

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [
                          {"id":"123c20bb-0cc9-4d62-8cc5-2bb7e05e5664","name":"Alex"},
                          {"id":"d50c63a2-85af-424f-9c6e-dc90f7f303f9","name":"Zoe"}
                        ]
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[1].email").doesNotExist());

        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldReturnEmptyJsonArray() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[]"));

        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
    }
}
