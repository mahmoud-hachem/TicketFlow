package com.ticketflow.ticket;

import com.ticketflow.agent.AgentRepository;
import com.ticketflow.config.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.ticketflow.ticket.TicketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketCommentTest {
    private static final String COMMENT_ID = "00000000-0000-4000-8000-000000000000";
    private static final String PATH = "/api/tickets/" + TICKET_ID + "/comments";
    private TicketRepository tickets;
    private AgentRepository agents;
    private TicketService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        agents = mock(AgentRepository.class);
        service = new TicketService(tickets, agents, Clock.fixed(UPDATED, ZoneOffset.UTC), () -> COMMENT_ID);
        mvc = MockMvcBuilders.standaloneSetup(new TicketController(service))
                .setControllerAdvice(new RestExceptionHandler()).build();
    }

    private Ticket available(TicketStatus status) {
        Ticket ticket = ticket(status, 4L, null);
        comments(ticket);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(tickets.save(ticket)).thenAnswer(call -> {
            ReflectionTestUtils.setField(ticket, "version", 5L);
            return ticket;
        });
        return ticket;
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"OPEN", "IN_PROGRESS", "RESOLVED"})
    void domainAppendsTrimmedCommentAndPreservesOtherFields(TicketStatus status) {
        Ticket ticket = ticket(status, 4L, AGENT_ID);
        comments(ticket);
        List<Comment> before = ticket.getComments();
        Comment created = ticket.addComment(COMMENT_ID, "  Author  ", "  First\nSecond  ", UPDATED);
        assertEquals("Author", created.authorName());
        assertEquals("First\nSecond", created.content());
        assertEquals(COMMENT_ID, created.id());
        assertEquals(UPDATED, created.createdAt());
        assertEquals(before, ticket.getComments().subList(0, before.size()));
        assertEquals(created, ticket.getComments().get(before.size()));
        assertEquals(UPDATED, ticket.getUpdatedAt());
        assertEquals(CREATED, ticket.getCreatedAt());
        assertEquals(status, ticket.getStatus());
        assertEquals(Priority.HIGH, ticket.getPriority());
        assertEquals(AGENT_ID, ticket.getAssignedAgentId());
        assertEquals(4L, ticket.getVersion());
    }

    static Stream<Arguments> invalidText() {
        return Stream.of(Arguments.of(null, "Content"), Arguments.of("", "Content"),
                Arguments.of(" \t\n ", "Content"), Arguments.of("a".repeat(101), "Content"),
                Arguments.of("Author", null), Arguments.of("Author", ""),
                Arguments.of("Author", " \t\n "), Arguments.of("Author", "c".repeat(2001)));
    }

    @ParameterizedTest
    @MethodSource("invalidText")
    void invalidDomainAndDirectServiceTextDoNotMutateOrLookup(String author, String content) {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, null);
        assertThrows(InvalidTicketInputException.class, () -> ticket.addComment(COMMENT_ID, author, content, UPDATED));
        assertTrue(ticket.getComments().isEmpty());
        assertEquals(CREATED, ticket.getUpdatedAt());
        assertThrows(InvalidTicketInputException.class, () -> service.addComment(TICKET_ID, new AddCommentRequest(author, content, 4L)));
        verifyNoInteractions(tickets, agents);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void exactTrimmedBoundariesAreAccepted(int authorLength) throws Exception {
        Ticket ticket = available(TicketStatus.OPEN);
        String author = "a".repeat(authorLength);
        String content = "c".repeat(authorLength == 100 ? 2000 : 1);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorName\":\"  " + author + "  \",\"content\":\"  " + content + "  \",\"expectedVersion\":4}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.comment.authorName").value(author))
                .andExpect(jsonPath("$.comment.content").value(content));
        assertEquals(4, ticket.getComments().size());
        verify(tickets).save(ticket);
    }

    @Test
    void apiReturnsApprovedContractAndChronologicalDetailsWithIdTies() throws Exception {
        Ticket ticket = available(TicketStatus.OPEN);
        // Tie the new comment timestamp with the existing first two comments.
        service = new TicketService(tickets, agents, Clock.fixed(CREATED, ZoneOffset.UTC), () -> COMMENT_ID);
        mvc = MockMvcBuilders.standaloneSetup(new TicketController(service)).setControllerAdvice(new RestExceptionHandler()).build();
        List<Comment> before = ticket.getComments();
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorName\":\" Author \",\"content\":\" Message \",\"expectedVersion\":4}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ticketId").value(TICKET_ID))
                .andExpect(jsonPath("$.ticketVersion").value(5))
                .andExpect(jsonPath("$.updatedAt").value(CREATED.toString()))
                .andExpect(jsonPath("$.comment.id").value(COMMENT_ID))
                .andExpect(jsonPath("$.comment.createdAt").value(CREATED.toString()));
        mvc.perform(get("/api/tickets/" + TICKET_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments.length()").value(4))
                .andExpect(jsonPath("$.comments[0].id").value(COMMENT_ID))
                .andExpect(jsonPath("$.comments[1].content").value("First tied comment"))
                .andExpect(jsonPath("$.comments[3].content").value("Later comment"));
        assertEquals(before, ticket.getComments().subList(0, before.size()));
        assertThrows(UnsupportedOperationException.class, () -> ticket.getComments().clear());
    }

    static Stream<String> invalidJson() {
        Stream<String> textValues = Stream.of("null", "\"\"", "\"   \"", "42", "true", "[]", "{}");
        Stream<String> authors = textValues.map(value -> "{\"authorName\":" + value + ",\"content\":\"Message\",\"expectedVersion\":4}");
        Stream<String> contents = Stream.of("null", "\"\"", "\"   \"", "42", "true", "[]", "{}")
                .map(value -> "{\"authorName\":\"Author\",\"content\":" + value + ",\"expectedVersion\":4}");
        Stream<String> versions = Stream.of("null", "-1", "4.5", "4.0", "\"4\"", "true", "[]", "{}", "9223372036854775808")
                .map(value -> "{\"authorName\":\"Author\",\"content\":\"Message\",\"expectedVersion\":" + value + "}");
        return Stream.concat(Stream.concat(authors, contents), Stream.concat(versions, Stream.of(
                "{}", "{", "null", "{\"authorName\":\"Author\",\"content\":\"Message\"}",
                "{\"content\":\"Message\",\"expectedVersion\":4}", "{\"authorName\":\"Author\",\"expectedVersion\":4}",
                "{\"authorName\":\"" + "a".repeat(101) + "\",\"content\":\"Message\",\"expectedVersion\":4}",
                "{\"authorName\":\"Author\",\"content\":\"" + "c".repeat(2001) + "\",\"expectedVersion\":4}")));
    }

    @ParameterizedTest
    @MethodSource("invalidJson")
    void apiValidationRejectsBeforeAnyRepositoryAccess(String json) throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(tickets, agents);
    }

    @ParameterizedTest
    @ValueSource(strings = {"closed", "missing", "stale", "lock", "nullVersion", "dangling", "nullComments"})
    void failedOperationsReturnProblemDetailsAndDoNotRetryOrSaveInvalidData(String mode) throws Exception {
        Ticket ticket = available(mode.equals("closed") ? TicketStatus.CLOSED : TicketStatus.OPEN);
        List<Comment> before = ticket.getComments();
        if (mode.equals("missing")) when(tickets.findById(TICKET_ID)).thenReturn(Optional.empty());
        if (mode.equals("stale")) ReflectionTestUtils.setField(ticket, "version", 6L);
        if (mode.equals("nullVersion")) ReflectionTestUtils.setField(ticket, "version", null);
        if (mode.equals("dangling")) ReflectionTestUtils.setField(ticket, "assignedAgentId", AGENT_ID);
        if (mode.equals("nullComments")) ReflectionTestUtils.setField(ticket, "comments", null);
        if (mode.equals("lock")) doThrow(new OptimisticLockingFailureException("Internal details")).when(tickets).save(ticket);
        int expectedStatus = mode.equals("missing") ? 404 : List.of("nullVersion", "dangling", "nullComments").contains(mode) ? 500 : 409;
        String code = mode.equals("missing") ? "TICKET_NOT_FOUND" : mode.equals("closed") ? "TICKET_CLOSED"
                : expectedStatus == 500 ? "INTERNAL_ERROR" : "VERSION_CONFLICT";
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorName\":\"Author\",\"content\":\"Message\",\"expectedVersion\":4}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value(code)).andExpect(jsonPath("$.instance").value(PATH));
        if (mode.equals("lock")) verify(tickets).save(ticket);
        else {
            verify(tickets, never()).save(any());
            if (!mode.equals("nullComments")) assertEquals(before, ticket.getComments());
            assertEquals(CREATED, ticket.getUpdatedAt());
        }
    }

    @Test
    void duplicateIdsAndClosedDomainOperationsPreserveComments() {
        Ticket ticket = ticket(TicketStatus.OPEN, 4L, null);
        ticket.addComment(COMMENT_ID, "Author", "Message", CREATED);
        assertThrows(TicketIntegrityException.class, () -> ticket.addComment(COMMENT_ID, "Another", "Other", UPDATED));
        assertEquals(1, ticket.getComments().size());
        assertEquals(CREATED, ticket.getUpdatedAt());
        Ticket closed = ticket(TicketStatus.CLOSED, 4L, null);
        assertThrows(TicketClosedException.class, () -> closed.addComment(COMMENT_ID, "Author", "Message", UPDATED));
        assertTrue(closed.getComments().isEmpty());
    }

    @Test
    void directInvalidArgumentsAndGeneratedIdsAreRejectedSafely() {
        assertThrows(InvalidTicketInputException.class, () -> service.addComment(TICKET_ID, null));
        assertThrows(InvalidTicketInputException.class, () -> service.addComment("invalid", new AddCommentRequest("Author", "Message", 4L)));
        assertThrows(InvalidTicketInputException.class, () -> service.addComment(TICKET_ID, new AddCommentRequest("Author", "Message", null)));
        assertThrows(InvalidTicketInputException.class, () -> service.addComment(TICKET_ID, new AddCommentRequest("Author", "Message", -1L)));
        verifyNoInteractions(tickets, agents);
        Ticket ticket = available(TicketStatus.OPEN);
        TicketService brokenGenerator = new TicketService(tickets, agents, Clock.fixed(UPDATED, ZoneOffset.UTC), () -> "invalid");
        assertThrows(TicketIntegrityException.class, () -> brokenGenerator.addComment(TICKET_ID, new AddCommentRequest("Author", "Message", 4L)));
        verify(tickets, never()).save(any());
        assertEquals(3, ticket.getComments().size());
    }

    @Test
    void zeroPreconditionUsesActualSavedVersion() {
        Ticket loaded = ticket(TicketStatus.OPEN, 0L, null);
        Ticket saved = ticket(TicketStatus.OPEN, 12L, null);
        saved.addComment(COMMENT_ID, "Author", "Message", UPDATED);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(loaded));
        when(tickets.save(loaded)).thenReturn(saved);
        CommentCreatedResponse response = service.addComment(TICKET_ID, new AddCommentRequest("Author", "Message", 0L));
        assertEquals(12L, response.ticketVersion());
        assertEquals(0L, loaded.getVersion());
        assertEquals(UPDATED, response.updatedAt());
    }
}
