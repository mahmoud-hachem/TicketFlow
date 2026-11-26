package com.ticketflow.integration;

import com.ticketflow.BackendApplication;
import com.ticketflow.agent.*;
import com.ticketflow.ticket.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Prepared real-MongoDB tests. Never select without explicit database authorization. */
@SpringBootTest(classes = BackendApplication.class)
@ContextConfiguration(initializers = IsolatedMongoTestInitializer.class)
@Import(TicketFlowMongoIT.TimeConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicketFlowMongoIT {
    private static final String FIRST_AGENT = "d50c63a2-85af-424f-9c6e-dc90f7f303f9";
    private static final String SECOND_AGENT = "123c20bb-0cc9-4d62-8cc5-2bb7e05e5664";
    @Autowired TicketRepository tickets;
    @Autowired AgentRepository agents;
    @Autowired MongoTemplate mongo;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext context;
    @Autowired BarrierClock clock;
    private MockMvc mvc;

    @BeforeAll
    void requireFreshConfirmedDatabaseBeforeTestWrites() {
        var settings = IsolatedMongoSettings.require(System.getenv(), Boolean.getBoolean("ticketflow.integration.enabled"));
        assertEquals(settings.database(), mongo.getDb().getName());
        assertEquals(0, tickets.count(), "Use a fresh isolated database; tests never clear data");
        assertEquals(0, agents.count(), "Use a fresh isolated database; tests never clear data");
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private MvcResult request(String method, String path, Object body, int status) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            default -> get(path);
        };
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(builder).andExpect(status().is(status)).andReturn();
    }

    private TicketDetailResponse detail(MvcResult result) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(), TicketDetailResponse.class);
    }

    private TicketDetailResponse create() throws Exception {
        var result = request("POST", "/api/tickets", new CreateTicketRequest(" Integration ticket ",
                " Persisted description ", " Integration requester ", Priority.HIGH), 201);
        TicketDetailResponse ticket = detail(result);
        assertEquals("/api/tickets/" + ticket.id(), result.getResponse().getHeader("Location"));
        return ticket;
    }

    private TicketDetailResponse assign(TicketDetailResponse ticket, String agent) throws Exception {
        TicketDetailResponse updated = detail(request("PATCH", "/api/tickets/" + ticket.id() + "/assignment",
                new AssignTicketRequest(agent, ticket.version()), 200));
        assertEquals(ticket.version() + 1, updated.version());
        return updated;
    }

    private TicketDetailResponse transition(TicketDetailResponse ticket, TicketStatus status) throws Exception {
        TicketDetailResponse updated = detail(request("PATCH", "/api/tickets/" + ticket.id() + "/status",
                new ChangeTicketStatusRequest(status, ticket.version()), 200));
        assertEquals(ticket.version() + 1, updated.version());
        return updated;
    }

    @Test @Order(1)
    void concurrentMissingAgentSeedingIsIdempotentAndPreservesEditedRecords() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Void> seed = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                new DemoAgentSeeder(agents).run(new DefaultApplicationArguments(new String[0]));
                return null;
            };
            Future<Void> first = executor.submit(seed), second = executor.submit(seed);
            start.countDown(); first.get(60, TimeUnit.SECONDS); second.get(60, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertEquals(3, agents.count());
        agents.save(new Agent(FIRST_AGENT, "Edited isolated demo agent", "edited@example.com"));
        new DemoAgentSeeder(agents).run(new DefaultApplicationArguments(new String[0]));
        assertEquals(3, agents.count());
        assertEquals("Edited isolated demo agent", agents.findById(FIRST_AGENT).orElseThrow().getName());
        assertEquals(0, tickets.count());
        Document raw = mongo.getCollection("agents").find(new Document("_id", FIRST_AGENT)).first();
        assertNotNull(raw); assertInstanceOf(String.class, raw.get("_id"));
        mvc.perform(get("/api/agents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test @Order(2)
    void fullApplicationStartupWithExplicitSeedingPreservesExistingAgents() {
        var builder = new SpringApplicationBuilder(BackendApplication.class).web(WebApplicationType.NONE)
                .initializers(new IsolatedMongoTestInitializer(), application -> application.getEnvironment()
                        .getPropertySources().addFirst(new MapPropertySource("authorizedSeedLifecycle",
                                Map.of("ticketflow.demo-seeding.enabled", "true"))));
        try (var application = builder.run()) {
            assertEquals(3, application.getBean(AgentRepository.class).count());
            assertEquals("Edited isolated demo agent", agents.findById(FIRST_AGENT).orElseThrow().getName());
        }
    }

    @Test @Order(3)
    void creationPersistenceBsonMappingAndDatabaseOrdering() throws Exception {
        TicketDetailResponse first = create(), second = create();
        assertEquals(TicketStatus.OPEN, first.status()); assertNull(first.assignedAgent());
        assertEquals("Integration ticket", first.title()); assertTrue(first.comments().isEmpty());
        assertNotNull(first.version()); assertTrue(first.version() >= 0);
        Ticket loaded = tickets.findById(first.id()).orElseThrow();
        assertEquals(first.createdAt(), loaded.getCreatedAt()); assertEquals(first.version(), loaded.getVersion());
        Document raw = mongo.getCollection("tickets").find(new Document("_id", first.id())).first();
        assertNotNull(raw); assertInstanceOf(String.class, raw.get("_id"));
        assertInstanceOf(Date.class, raw.get("createdAt")); assertInstanceOf(Long.class, raw.get("version"));
        var list = tickets.findAllByOrderByCreatedAtDescIdAsc();
        assertEquals(List.of(first.id(), second.id()).stream().sorted().toList(), list.stream().map(Ticket::getId).toList());
        mvc.perform(get("/api/tickets")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test @Order(4)
    void completeAssignmentStatusAndCommentWorkflowPersistsAndVersionsEveryMutation() throws Exception {
        TicketDetailResponse ticket = create();
        ticket = assign(ticket, FIRST_AGENT); ticket = assign(ticket, SECOND_AGENT); ticket = assign(ticket, SECOND_AGENT);
        ticket = transition(ticket, TicketStatus.IN_PROGRESS);
        for (int i = 0; i < 2; i++) {
            var response = request("POST", "/api/tickets/" + ticket.id() + "/comments",
                    new AddCommentRequest(" Author ", " Message " + i + " ", ticket.version()), 201);
            CommentCreatedResponse created = json.readValue(response.getResponse().getContentAsString(), CommentCreatedResponse.class);
            assertEquals(ticket.version() + 1, created.ticketVersion());
            assertEquals("Author", created.comment().authorName());
            ticket = detail(request("GET", "/api/tickets/" + ticket.id(), null, 200));
            assertEquals(i + 1, ticket.comments().size());
        }
        assertEquals(ticket.comments().stream().map(CommentResponse::id).sorted().toList(),
                ticket.comments().stream().map(CommentResponse::id).toList());
        ticket = transition(ticket, TicketStatus.RESOLVED); ticket = transition(ticket, TicketStatus.IN_PROGRESS);
        ticket = transition(ticket, TicketStatus.RESOLVED); ticket = transition(ticket, TicketStatus.CLOSED);
        assertEquals(SECOND_AGENT, tickets.findById(ticket.id()).orElseThrow().getAssignedAgentId());
        assertEquals(2, tickets.findById(ticket.id()).orElseThrow().getComments().size());
        long version = ticket.version();
        request("PATCH", "/api/tickets/" + ticket.id() + "/assignment", new AssignTicketRequest(FIRST_AGENT, version), 409);
        request("PATCH", "/api/tickets/" + ticket.id() + "/status", new ChangeTicketStatusRequest(TicketStatus.IN_PROGRESS, version), 409);
        request("POST", "/api/tickets/" + ticket.id() + "/comments", new AddCommentRequest("Author", "No write", version), 409);
        assertEquals(version, tickets.findById(ticket.id()).orElseThrow().getVersion());
    }

    @Test @Order(5)
    void validationMissingResourcesAndStaleBrowserRequestsDoNotPersist() throws Exception {
        long before = tickets.count();
        request("POST", "/api/tickets", new CreateTicketRequest(" ", "Description", "Requester", Priority.LOW), 400);
        assertEquals(before, tickets.count());
        TicketDetailResponse ticket = create();
        request("PATCH", "/api/tickets/" + ticket.id() + "/status", new ChangeTicketStatusRequest(TicketStatus.IN_PROGRESS, ticket.version()), 409);
        request("PATCH", "/api/tickets/" + ticket.id() + "/assignment", new AssignTicketRequest(UUID.randomUUID().toString(), ticket.version()), 404);
        request("GET", "/api/tickets/" + UUID.randomUUID(), null, 404);
        long stale = ticket.version(); ticket = assign(ticket, FIRST_AGENT);
        Document snapshot = mongo.getCollection("tickets").find(new Document("_id", ticket.id())).first();
        for (var operation : List.of("assignment", "status", "comments")) {
            Object body = switch (operation) {
                case "assignment" -> new AssignTicketRequest(SECOND_AGENT, stale);
                case "status" -> new ChangeTicketStatusRequest(TicketStatus.IN_PROGRESS, stale);
                default -> new AddCommentRequest("Author", "Stale comment", stale);
            };
            var result = request(operation.equals("comments") ? "POST" : "PATCH",
                    "/api/tickets/" + ticket.id() + "/" + operation, body, 409);
            assertTrue(result.getResponse().getContentAsString().contains("VERSION_CONFLICT"));
        }
        assertEquals(snapshot, mongo.getCollection("tickets").find(new Document("_id", ticket.id())).first());
    }

    @Test @Order(6)
    void repositoryCompetingCopiesCannotOverwriteWinningComments() throws Exception {
        TicketDetailResponse created = create();
        Ticket first = tickets.findById(created.id()).orElseThrow(), stale = tickets.findById(created.id()).orElseThrow();
        String winning = UUID.randomUUID().toString();
        first.addComment(winning, "Winner", "Preserve me", clock.instant()); tickets.save(first);
        stale.addComment(UUID.randomUUID().toString(), "Loser", "Do not persist", clock.instant());
        assertThrows(OptimisticLockingFailureException.class, () -> tickets.save(stale));
        Ticket actual = tickets.findById(created.id()).orElseThrow();
        assertEquals(created.version() + 1, actual.getVersion());
        assertEquals(List.of(winning), actual.getComments().stream().map(Comment::id).toList());
    }

    @Test @Order(7)
    void competingHttpWritesAfterVersionChecksYieldOneSuccessAndOneConflict() throws Exception {
        TicketDetailResponse ticket = create();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        clock.barrier = new CyclicBarrier(2);
        try {
            Callable<MvcResult> first = () -> mvc.perform(patch("/api/tickets/" + ticket.id() + "/assignment")
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new AssignTicketRequest(FIRST_AGENT, ticket.version())))).andReturn();
            Callable<MvcResult> second = () -> mvc.perform(patch("/api/tickets/" + ticket.id() + "/assignment")
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new AssignTicketRequest(SECOND_AGENT, ticket.version())))).andReturn();
            Future<MvcResult> a = executor.submit(first), b = executor.submit(second);
            var results = List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), results.stream().map(result -> result.getResponse().getStatus()).sorted().toList());
            for (MvcResult result : results) {
                if (result.getResponse().getStatus() == 409) {
                    assertTrue(result.getResponse().getContentAsString().contains("VERSION_CONFLICT"));
                }
            }
            Ticket actual = tickets.findById(ticket.id()).orElseThrow();
            assertEquals(ticket.version() + 1, actual.getVersion());
        } finally { clock.barrier = null; executor.shutdownNow(); }
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary BarrierClock integrationClock() { return new BarrierClock(); }
    }

    static class BarrierClock extends Clock {
        volatile CyclicBarrier barrier;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), zone); }
        public Instant instant() {
            CyclicBarrier current = barrier;
            if (current != null) {
                try { current.await(20, TimeUnit.SECONDS); }
                catch (Exception ex) { throw new IllegalStateException("Competing-write synchronization failed"); }
            }
            return Instant.parse("2026-11-26T10:00:00Z");
        }
    }
}
