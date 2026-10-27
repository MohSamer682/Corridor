package com.corridor.office.common.domain;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {

    public ConflictException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
