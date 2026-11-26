
package com.ticketflow.config;

import jakarta.servlet.http.HttpServletRequest;
import com.ticketflow.agent.AgentNotFoundException;
import com.ticketflow.ticket.TicketClosedException;
import com.ticketflow.ticket.TicketIntegrityException;
import com.ticketflow.ticket.TicketNotFoundException;
import com.ticketflow.ticket.TicketVersionConflictException;
import com.ticketflow.ticket.InvalidTicketInputException;
import com.ticketflow.ticket.InvalidTransitionException;
import com.ticketflow.ticket.AssigneeRequiredException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponse;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );

        problem.setTitle("Validation Error");
        problem.setProperty("errorCode", "VALIDATION_ERROR");

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );

        problem.setProperty("fieldErrors", fieldErrors);

        return completeProblem(problem, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid JSON or unsupported field value"
        );

        problem.setTitle("Validation Error");
        problem.setProperty("errorCode", "VALIDATION_ERROR");

        return completeProblem(problem, request);
    }

    @ExceptionHandler(InvalidTicketInputException.class)
    public ProblemDetail handleInvalidArgument(
            InvalidTicketInputException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid input"
        );

        problem.setTitle("Validation Error");
        problem.setProperty("errorCode", "VALIDATION_ERROR");

        return completeProblem(problem, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        if (ex.getStatusCode().is5xxServerError()) {
            return frameworkError(ex, ex, request);
        }
        ProblemDetail problem = ex.getBody();

        if (ex.getStatusCode().value() == 404
                && "Ticket not found".equals(ex.getReason())) {

            problem.setTitle("Not Found");
            problem.setProperty(
                    "errorCode",
                    "TICKET_NOT_FOUND"
            );
        }

        return new ResponseEntity<>(completeProblem(problem, request),
                ex.getHeaders(), ex.getStatusCode());
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ProblemDetail handleTicketNotFound(
            TicketNotFoundException ex, HttpServletRequest request
    ) {
        return problem(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket not found", request);
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ProblemDetail handleAgentNotFound(
            AgentNotFoundException ex, HttpServletRequest request
    ) {
        return problem(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent not found", request);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ProblemDetail handleInvalidTransition(
            InvalidTransitionException ex, HttpServletRequest request
    ) {
        return problem(HttpStatus.CONFLICT, "INVALID_TRANSITION",
                "This status transition is not permitted.", request);
    }

    @ExceptionHandler(AssigneeRequiredException.class)
    public ProblemDetail handleAssigneeRequired(
            AssigneeRequiredException ex, HttpServletRequest request
    ) {
        return problem(HttpStatus.CONFLICT, "ASSIGNEE_REQUIRED",
                "Assign a support agent before entering IN_PROGRESS.", request);
    }

    @ExceptionHandler(TicketClosedException.class)
    public ProblemDetail handleClosedTicket(
            TicketClosedException ex, HttpServletRequest request
    ) {
        return problem(HttpStatus.CONFLICT, "TICKET_CLOSED",
                request.getRequestURI().endsWith("/status")
                        ? "Closed tickets cannot change status."
                        : "Closed tickets cannot be assigned or reassigned.", request);
    }

    @ExceptionHandler({TicketVersionConflictException.class,
            OptimisticLockingFailureException.class})
    public ProblemDetail handleVersionConflict(
            Exception ex, HttpServletRequest request
    ) {
        return problem(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "This ticket changed since you opened it. Reload and try again.", request);
    }

    @ExceptionHandler(TicketIntegrityException.class)
    public ProblemDetail handleIntegrityError(
            TicketIntegrityException ex, HttpServletRequest request
    ) {
        logInternalFailure(ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Stored ticket data is inconsistent.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex, HttpServletRequest request
    ) {
        if (ex instanceof ErrorResponse error) {
            return frameworkError(error, ex, request);
        }
        logInternalFailure(ex);
        return ResponseEntity.internalServerError().body(problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", request));
    }

    private ResponseEntity<ProblemDetail> frameworkError(
            ErrorResponse error, Exception ex, HttpServletRequest request
    ) {
        ProblemDetail body;
        if (error.getStatusCode().is5xxServerError()) {
            logInternalFailure(ex);
            body = problem(error.getStatusCode(), "INTERNAL_ERROR",
                    "An unexpected error occurred.", request);
        } else {
            body = completeProblem(error.getBody(), request);
        }
        return new ResponseEntity<>(body, error.getHeaders(), error.getStatusCode());
    }

    private void logInternalFailure(Exception ex) {
        StackTraceElement[] trace = ex.getStackTrace();
        String origin = trace.length == 0 ? "unavailable"
                : trace[0].getClassName() + "." + trace[0].getMethodName()
                        + ":" + trace[0].getLineNumber();
        // Exception messages, causes and request data may contain secrets; never log them here.
        logger.error("Internal request failure: exceptionType={}, origin={}",
                ex.getClass().getName(), origin);
    }

    private ProblemDetail problem(
            HttpStatusCode status, String errorCode, String detail, HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("errorCode", errorCode);
        return completeProblem(problem, request);
    }

    private ProblemDetail completeProblem(
            ProblemDetail problem,
            HttpServletRequest request
    ) {
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));

        return problem;
    }
}
