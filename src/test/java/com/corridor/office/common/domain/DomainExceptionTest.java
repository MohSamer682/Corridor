package com.corridor.office.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DomainExceptionTest {

    @Test
    void shouldReportConflictStatusAndCode() {
        var exception = new ConflictException("probe.taken", "room");

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.code()).isEqualTo("CONFLICT");
        assertThat(exception.messageKey()).isEqualTo("probe.taken");
    }

    @Test
    void shouldNotLetCallersChangeTheMessageArguments() {
        var exception = new NotFoundException("probe.missing", 7);

        exception.arguments()[0] = 8;

        assertThat(exception.arguments()).containsExactly(7);
    }
}
