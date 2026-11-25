
package com.ticketflow.ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.ticketflow.config.RestExceptionHandler;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketControllerTest {

    private MockMvc mockMvc;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new TicketController(ticketService))
                .setControllerAdvice(new RestExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldCreateValidTicket() throws Exception {

        Instant now = Instant.parse("2026-11-25T10:00:00Z");

        TicketDetailResponse response =
                new TicketDetailResponse(
                        "test-id",
                        "Printer problem",
                        "Printer is not responding",
                        "Test User",
                        Priority.HIGH,
                        TicketStatus.OPEN,
                        null,
                        List.of(),
                        now,
                        now,
                        0L
                );

        when(ticketService.createTicket(
                any(CreateTicketRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Printer problem",
                      "description": "Printer is not responding",
                      "requesterName": "Test User",
                      "priority": "HIGH"
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", "/api/tickets/test-id"))
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        verify(ticketService).createTicket(
                any(CreateTicketRequest.class));
    }

    @Test
    void shouldRejectBlankTitle() throws Exception {

        mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "",
                      "description": "Printer problem",
                      "requesterName": "Test User",
                      "priority": "HIGH"
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectInvalidPriority() throws Exception {

        mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Printer problem",
                      "description": "Printer problem",
                      "requesterName": "Test User",
                      "priority": "URGENT"
                    }
                    """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectMissingFields() throws Exception {

        mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.requesterName").exists())
                .andExpect(jsonPath("$.fieldErrors.priority").exists());

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectBlankDescription() throws Exception {

        mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Printer problem",
                      "description": "   ",
                      "requesterName": "Test User",
                      "priority": "HIGH"
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectOverLimitFields() throws Exception {

        String json = """
                {
                  "title": "%s",
                  "description": "%s",
                  "requesterName": "%s",
                  "priority": "HIGH"
                }
                """.formatted(
                    "A".repeat(121),
                    "B".repeat(2001),
                    "C".repeat(101)
                );

        mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.requesterName").exists());

        verifyNoInteractions(ticketService);
    }
}
