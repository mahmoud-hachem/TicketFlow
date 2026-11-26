
package com.ticketflow.ticket;

import org.springframework.stereotype.Service;
import com.ticketflow.agent.Agent;
import com.ticketflow.agent.AgentNotFoundException;
import com.ticketflow.agent.AgentRepository;
import com.ticketflow.agent.AgentSummaryResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final AgentRepository agentRepository;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    public TicketService(
            TicketRepository ticketRepository,
            AgentRepository agentRepository,
            Clock clock,
            Supplier<String> idGenerator
    ) {
        this.ticketRepository = ticketRepository;
        this.agentRepository = agentRepository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public TicketDetailResponse createTicket(
            CreateTicketRequest request
    ) {
        Ticket ticket = new Ticket(
                idGenerator.get(),
                request.title(),
                request.description(),
                request.requesterName(),
                request.priority(),
                Instant.now(clock)
        );

        Ticket saved = ticketRepository.save(ticket);

        return toDetailResponse(saved);
    }

    public List<TicketSummaryResponse> listTickets() {
        List<Ticket> tickets = ticketRepository.findAllByOrderByCreatedAtDescIdAsc();
        tickets.forEach(this::requirePersistedVersion);

        Set<String> agentIds = tickets.stream()
                .map(Ticket::getAssignedAgentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, AgentSummaryResponse> agents = new HashMap<>();
        if (!agentIds.isEmpty()) {
            agentRepository.findAllById(agentIds).forEach(agent ->
                    agents.put(agent.getId(), toAgentSummary(agent)));
        }

        return tickets.stream()
                .map(ticket -> new TicketSummaryResponse(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getRequesterName(),
                        ticket.getStatus(),
                        ticket.getPriority(),
                        assignedAgent(ticket, agents),
                        ticket.getCreatedAt(),
                        ticket.getUpdatedAt(),
                        ticket.getVersion()
                ))
                .toList();
    }

    public TicketDetailResponse getTicketById(String id) {
        return toDetailResponse(loadTicket(id));
    }

    public TicketDetailResponse assignAgent(String id, AssignTicketRequest request) {
        Ticket.requireUuidString(id, "Ticket ID");
        if (request == null) {
            throw new InvalidTicketInputException("Assignment request is required");
        }
        Ticket.requireUuidString(request.agentId(), "Agent ID");
        requireExpectedVersion(request.expectedVersion(), "Expected version must be nonnegative");
        Ticket ticket = loadCurrentTicket(id, request.expectedVersion());

        // Reject predictable mapping failures and dangling current assignments before writing.
        requireDetailIntegrity(ticket);
        commentResponses(ticket);
        String currentAgentId = ticket.getAssignedAgentId();
        boolean sameAgent = request.agentId().equals(currentAgentId);
        if (currentAgentId != null && !sameAgent
                && !agentRepository.existsById(currentAgentId)) {
            throw new TicketIntegrityException("Assigned agent reference does not exist");
        }

        Agent agent = agentRepository.findById(request.agentId()).orElseThrow(() -> {
            if (sameAgent) {
                return new TicketIntegrityException("Assigned agent reference does not exist");
            }
            return new AgentNotFoundException();
        });
        if (!request.agentId().equals(agent.getId())) {
            throw new TicketIntegrityException("Agent lookup returned a different identifier");
        }
        Instant at = mutationTime("Assignment");
        ticket.assignAgent(agent.getId(), at);
        Ticket saved = ticketRepository.save(ticket);

        // Reuse the validated agent while mapping the assignment actually saved.
        return toDetailResponse(saved, Map.of(agent.getId(), toAgentSummary(agent)));
    }

    public TicketDetailResponse changeStatus(String id, ChangeTicketStatusRequest request) {
        Ticket.requireUuidString(id, "Ticket ID");
        if (request == null || request.status() == null) {
            throw new InvalidTicketInputException("Status and nonnegative expected version are required");
        }
        requireExpectedVersion(request.expectedVersion(), "Status and nonnegative expected version are required");
        Ticket ticket = loadCurrentTicket(id, request.expectedVersion());
        // Resolve references and validate response data before any mutation or save.
        TicketDetailResponse current = toDetailResponse(ticket);
        Map<String, AgentSummaryResponse> agents = ticket.getAssignedAgentId() == null
                ? Map.of() : Map.of(ticket.getAssignedAgentId(),
                        current.assignedAgent());
        Instant at = mutationTime("Status");
        ticket.changeStatus(request.status(), at);
        return toDetailResponse(ticketRepository.save(ticket), agents);
    }

    public CommentCreatedResponse addComment(String id, AddCommentRequest request) {
        Ticket.requireUuidString(id, "Ticket ID");
        if (request == null) {
            throw new InvalidTicketInputException("Comment and nonnegative expected version are required");
        }
        requireExpectedVersion(request.expectedVersion(), "Comment and nonnegative expected version are required");
        Comment.validate(request.authorName(), 100);
        Comment.validate(request.content(), 2000);
        Ticket ticket = loadCurrentTicket(id, request.expectedVersion());
        toDetailResponse(ticket);
        String commentId = idGenerator.get();
        try {
            Ticket.requireUuidString(commentId, "Comment ID");
        } catch (InvalidTicketInputException ex) {
            throw new TicketIntegrityException("Comment generator returned an invalid identifier");
        }
        Instant at = mutationTime("Comment");
        ticket.addComment(commentId, request.authorName(), request.content(), at);
        Ticket saved = ticketRepository.save(ticket);
        requireDetailIntegrity(saved);
        CommentResponse created = commentResponses(saved).stream()
                .filter(comment -> comment.id().equals(commentId)).findFirst()
                .orElseThrow(() -> new TicketIntegrityException("Saved comment is missing"));
        return new CommentCreatedResponse(saved.getId(), created, saved.getVersion(), saved.getUpdatedAt());
    }

    private void requireExpectedVersion(Long expectedVersion, String message) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new InvalidTicketInputException(message);
        }
    }

    private Ticket loadCurrentTicket(String id, Long expectedVersion) {
        Ticket ticket = loadTicket(id);
        requirePersistedVersion(ticket);
        if (!ticket.getVersion().equals(expectedVersion)) {
            throw new TicketVersionConflictException();
        }
        return ticket;
    }

    private Instant mutationTime(String operation) {
        Instant at = Instant.now(clock);
        if (at == null) {
            throw new TicketIntegrityException(operation + " clock returned no timestamp");
        }
        return at;
    }

    private Ticket loadTicket(String id) {
        return ticketRepository.findById(id)
                .orElseThrow(TicketNotFoundException::new);
    }

    private void requirePersistedVersion(Ticket ticket) {
        if (ticket == null || ticket.getVersion() == null || ticket.getVersion() < 0) {
            throw new TicketIntegrityException("Ticket has no valid persisted version");
        }
    }

    private void requireDetailIntegrity(Ticket ticket) {
        requirePersistedVersion(ticket);
        if (ticket.getId() == null || ticket.getTitle() == null
                || ticket.getDescription() == null || ticket.getRequesterName() == null
                || ticket.getStatus() == null || ticket.getPriority() == null
                || ticket.getCreatedAt() == null || ticket.getUpdatedAt() == null) {
            throw new TicketIntegrityException("Ticket detail fields are missing");
        }
    }

    private List<CommentResponse> commentResponses(Ticket ticket) {
        List<Comment> comments;
        try {
            comments = ticket.getComments();
        } catch (NullPointerException ex) {
            throw new TicketIntegrityException("Ticket comments are missing or contain null entries");
        }
        for (Comment comment : comments) {
            if (comment.id() == null || comment.authorName() == null
                    || comment.content() == null || comment.createdAt() == null) {
                throw new TicketIntegrityException("Comment detail fields are missing");
            }
        }
        return comments.stream()
                .sorted(Comparator.comparing(Comment::createdAt).thenComparing(Comment::id))
                .map(comment -> new CommentResponse(comment.id(), comment.authorName(),
                        comment.content(), comment.createdAt()))
                .toList();
    }

    private AgentSummaryResponse toAgentSummary(Agent agent) {
        return new AgentSummaryResponse(agent.getId(), agent.getName());
    }

    private AgentSummaryResponse assignedAgent(
            Ticket ticket, Map<String, AgentSummaryResponse> agents
    ) {
        if (ticket.getAssignedAgentId() == null) {
            return null;
        }
        AgentSummaryResponse agent = agents.get(ticket.getAssignedAgentId());
        if (agent == null) {
            throw new TicketIntegrityException("Assigned agent reference does not exist");
        }
        return agent;
    }

    private TicketDetailResponse toDetailResponse(Ticket ticket) {
        requireDetailIntegrity(ticket);
        if (ticket.getAssignedAgentId() == null) {
            return toDetailResponse(ticket, Map.of());
        }
        Agent agent = agentRepository.findById(ticket.getAssignedAgentId())
                .orElseThrow(() -> new TicketIntegrityException(
                        "Assigned agent reference does not exist"));
        return toDetailResponse(ticket, Map.of(agent.getId(), toAgentSummary(agent)));
    }

    private TicketDetailResponse toDetailResponse(
            Ticket ticket, Map<String, AgentSummaryResponse> agents
    ) {
        requireDetailIntegrity(ticket);
        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getRequesterName(),
                ticket.getPriority(),
                ticket.getStatus(),
                assignedAgent(ticket, agents),
                commentResponses(ticket),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getVersion()
        );
    }
}
