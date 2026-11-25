
package com.ticketflow.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(
            IllegalArgumentException ex,
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
    public ProblemDetail handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ex.getBody();

        if (ex.getStatusCode().value() == 404
                && "Ticket not found".equals(ex.getReason())) {

            problem.setTitle("Not Found");
            problem.setProperty(
                    "errorCode",
                    "TICKET_NOT_FOUND"
            );
        }

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
