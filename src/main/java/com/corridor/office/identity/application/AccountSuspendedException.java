package com.corridor.office.identity.application;

import com.corridor.office.common.domain.DomainException;
import org.springframework.http.HttpStatus;

public class AccountSuspendedException extends DomainException {

    public AccountSuspendedException() {
        super("auth.accountSuspended");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }

    @Override
    public String code() {
        return "ACCOUNT_SUSPENDED";
    }
}
