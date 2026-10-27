package com.corridor.office.identity.application;

import com.corridor.office.common.domain.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("auth.invalidCredentials");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }

    @Override
    public String code() {
        return "INVALID_CREDENTIALS";
    }
}
