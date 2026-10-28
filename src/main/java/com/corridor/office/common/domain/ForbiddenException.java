package com.corridor.office.common.domain;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends DomainException {

    public ForbiddenException(String messageKey) {
        super(messageKey);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
