package dev.springbootstaticanalysis.api;

import dev.springbootstaticanalysis.shared.ConflictException;
import dev.springbootstaticanalysis.shared.InvalidRequestException;
import dev.springbootstaticanalysis.shared.NotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Request conflicts with current state", exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:spring-boot-static-analysis:problem:"
                + title.toLowerCase().replace(' ', '-')));
        return problem;
    }
}
