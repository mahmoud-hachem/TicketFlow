package com.ticketflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.*;

class ContextIsolationTest {
    @Test
    void fullApplicationScanningDoesNotDiscoverUnitTestControllers() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var names = scanner.findCandidateComponents("com.ticketflow").stream()
                .map(definition -> definition.getBeanClassName()).toList();
        assertTrue(names.contains("com.ticketflow.ticket.TicketController"));
        assertTrue(names.contains("com.ticketflow.agent.AgentController"));
        assertFalse(names.stream().anyMatch(name -> name.contains("$FailureController")));
    }
}
