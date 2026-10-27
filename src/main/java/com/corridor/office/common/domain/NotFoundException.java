package com.corridor.office.common.domain;

import org.springframework.http.HttpStatus;

public class NotFoundException extends DomainException {

    public NotFoundException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
