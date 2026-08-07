package com.cmacgm.gbs.rst.api.common.error;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.cmacgm.gbs.rst.api.tms.domain.TmsStateException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
        return problem(
                exception.status(),
                exception.code(),
                exception.status().getReasonPhrase(),
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(TmsStateException.class)
    ProblemDetail handleTmsState(TmsStateException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "tms-session-conflict",
                "TMS session conflict",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, ignored) -> first));
        ProblemDetail problem = problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation-error",
                "Validation failed",
                "One or more request fields are invalid.",
                request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "data-conflict",
                "Data conflict",
                "The requested change conflicts with the current data.",
                request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(
            OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "optimistic-lock-conflict",
                "Concurrent update",
                "The resource changed concurrently; reload it and retry.",
                request);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://rst.cmacgm.com/problems/" + code));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
