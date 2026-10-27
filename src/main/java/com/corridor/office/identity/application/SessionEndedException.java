package com.corridor.office.identity.application;

import com.corridor.office.common.domain.DomainException;
import org.springframework.http.HttpStatus;

public class SessionEndedException extends DomainException {

    public SessionEndedException() {
        super("auth.sessionEnded");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }

    @Override
    public String code() {
        return "SESSION_ENDED";
    }
}
