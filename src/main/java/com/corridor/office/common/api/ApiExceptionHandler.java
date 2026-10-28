package com.corridor.office.common.api;

import com.corridor.office.common.domain.DomainException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MessageSource messages;

    ApiExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException exception, Locale locale) {
        var detail = messages.getMessage(exception.messageKey(), exception.arguments(), locale);
        return ResponseEntity.status(exception.status())
                .headers(exception.headers())
                .body(problem(exception.status(), detail, exception.code()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleUnauthenticated(AuthenticationException exception, Locale locale) {
        var detail = messages.getMessage("auth.unauthenticated", null, locale);
        return problem(HttpStatus.UNAUTHORIZED, detail, "UNAUTHENTICATED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleForbidden(AccessDeniedException exception, Locale locale) {
        var detail = messages.getMessage("auth.forbidden", null, locale);
        return problem(HttpStatus.FORBIDDEN, detail, "FORBIDDEN");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, Locale locale) {
        log.error("Unexpected error", exception);
        var detail = messages.getMessage("error.unexpected", null, locale);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, detail, "INTERNAL_ERROR");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = messages.getMessage("error.validation", null, LocaleContextHolder.getLocale());
        var body = problem(HttpStatus.UNPROCESSABLE_CONTENT, detail, "VALIDATION_FAILED");
        body.setProperty(
                "errors",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                        .toList());
        return handleExceptionInternal(exception, body, headers, HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
