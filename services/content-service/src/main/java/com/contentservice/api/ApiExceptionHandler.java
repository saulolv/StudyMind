package com.contentservice.api;

import com.contentservice.catalog.ContentNotFoundException;
import com.contentservice.ingestion.InvalidContentSourceException;
import com.contentservice.web.UnauthenticatedException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One error shape for the whole service: RFC 9457 problem details. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidContentSourceException.class)
    ProblemDetail onInvalidSource(InvalidContentSourceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-content-source", "Invalid content source", ex.getMessage());
    }

    @ExceptionHandler(ContentNotFoundException.class)
    ProblemDetail onNotFound(ContentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "content-not-found", "Content not found", ex.getMessage());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ProblemDetail onUnauthenticated(UnauthenticatedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Unauthenticated", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://studymind/problems/" + type));
        problem.setTitle(title);
        return problem;
    }
}
