package com.corridor.office.presence.domain;

import com.corridor.office.common.domain.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidMoveException extends DomainException {

    public InvalidMoveException() {
        super("presence.invalidMove");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}
