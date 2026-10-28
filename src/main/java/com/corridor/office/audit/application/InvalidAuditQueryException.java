package com.corridor.office.audit.application;

import com.corridor.office.common.domain.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidAuditQueryException extends DomainException {

    public InvalidAuditQueryException(String messageKey) {
        super(messageKey);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
