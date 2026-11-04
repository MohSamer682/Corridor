package com.corridor.office.common.domain;

import org.springframework.http.HttpStatus;

/** Input that is well formed but breaks a rule, such as a message that is too long. */
public class InvalidInputException extends DomainException {

    public InvalidInputException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}
