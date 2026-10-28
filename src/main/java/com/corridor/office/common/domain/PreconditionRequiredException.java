package com.corridor.office.common.domain;

import org.springframework.http.HttpStatus;

public class PreconditionRequiredException extends DomainException {

    public PreconditionRequiredException(String messageKey) {
        super(messageKey);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.PRECONDITION_REQUIRED;
    }
}
