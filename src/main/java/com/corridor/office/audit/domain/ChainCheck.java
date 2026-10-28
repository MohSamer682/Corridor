package com.corridor.office.audit.domain;

import java.util.Optional;

public record ChainCheck(long checked, Optional<AuditEvent> firstAltered) {

    public boolean intact() {
        return firstAltered.isEmpty();
    }
}
