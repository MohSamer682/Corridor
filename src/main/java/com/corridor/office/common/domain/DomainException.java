package com.corridor.office.common.domain;

import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] arguments;

    protected DomainException(String messageKey, Object... arguments) {
        super(messageKey);
        this.messageKey = messageKey;
        this.arguments = arguments.clone();
    }

    public String messageKey() {
        return messageKey;
    }

    public Object[] arguments() {
        return arguments.clone();
    }

    public abstract HttpStatus status();

    public String code() {
        return status().name();
    }
}
