package com.corridor.office.identity.application;

import com.corridor.office.common.domain.DomainException;
import org.springframework.http.HttpStatus;

public class AccountPendingException extends DomainException {

    public AccountPendingException() {
        super("auth.accountPending");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }

    @Override
    public String code() {
        return "ACCOUNT_PENDING";
    }
}
