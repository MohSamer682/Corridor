package com.corridor.office.identity.application;

import com.corridor.office.common.domain.DomainException;
import java.time.Duration;
import org.springframework.http.HttpStatus;

public class TooManyAttemptsException extends DomainException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("auth.tooManyAttempts");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }

    @Override
    public String code() {
        return "TOO_MANY_ATTEMPTS";
    }
}
