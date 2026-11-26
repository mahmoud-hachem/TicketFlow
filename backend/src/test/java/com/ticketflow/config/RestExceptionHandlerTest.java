package com.ticketflow.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ticketflow.ticket.InvalidTicketInputException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RestExceptionHandlerTest {

    private MockMvc mvc(RuntimeException failure) {
        return MockMvcBuilders.standaloneSetup(new FailureController(failure))
                .setControllerAdvice(new RestExceptionHandler()).build();
    }

    @Test
    void shouldPreserveResponseStatusExceptionStatusAndHeaders() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "60");
        ResponseStatusException failure = new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "Try again later") {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };

        mvc(failure).perform(get("/test/failure"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Try again later"))
                .andExpect(jsonPath("$.instance").value("/test/failure"));
    }

    @Test
    void shouldPreserveFrameworkServerStatusAndHeadersWithSafeDetails() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "30");
        ErrorResponseException failure = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "SENSITIVE_SERVER_MARKER"), null) {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };

        mvc(failure).perform(get("/test/failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("SENSITIVE_SERVER_MARKER"))));
    }

    @Test
    void shouldPreserveResponseStatusExceptionServerStatusAndHeadersWithSafeDetails()
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "15");
        ResponseStatusException failure = new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "SENSITIVE_REASON_MARKER") {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };

        mvc(failure).perform(get("/test/failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "15"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("SENSITIVE_REASON_MARKER"))));
    }

    @Test
    void shouldPreserveLegacyTicketNotFoundProblemContract() throws Exception {
        mvc(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"))
                .perform(get("/test/failure"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Ticket not found"));
    }

    @Test
    void shouldKeepTypedInputFailuresAsValidationErrors() throws Exception {
        mvc(new InvalidTicketInputException("Internal validation description"))
                .perform(get("/test/failure"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").value("Invalid input"));
    }

    @Test
    void shouldLogInternalIllegalArgumentWithoutSensitiveMessagesCausesOrRequestData()
            throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            IllegalArgumentException failure = new IllegalArgumentException(
                    "SENSITIVE_DIAGNOSTIC_MARKER", new RuntimeException("SENSITIVE_CAUSE_MARKER"));

            mvc(failure).perform(get("/test/failure").param("token", "PRIVATE_QUERY_MARKER"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                    .andExpect(content().string(not(containsString("SENSITIVE_DIAGNOSTIC_MARKER"))));

            assertFalse(appender.list.isEmpty());
            String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + right);
            assertTrue(messages.contains("java.lang.IllegalArgumentException"));
            assertTrue(messages.contains("origin="));
            assertFalse(messages.contains("SENSITIVE_DIAGNOSTIC_MARKER"));
            assertFalse(messages.contains("SENSITIVE_CAUSE_MARKER"));
            assertFalse(messages.contains("PRIVATE_QUERY_MARKER"));
            assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @RestController
    static class FailureController {
        private final RuntimeException failure;

        FailureController(RuntimeException failure) {
            this.failure = failure;
        }

        @GetMapping("/test/failure")
        public Object fail() {
            throw failure;
        }
    }
}
