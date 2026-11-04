package com.corridor.office.meetings.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MeetingStatusTest {

    @ParameterizedTest
    @CsvSource({
        "SCHEDULED, LIVE, true",
        "SCHEDULED, CANCELLED, true",
        "SCHEDULED, ENDED, true",
        "SCHEDULED, SCHEDULED, false",
        "LIVE, ENDED, true",
        "LIVE, CANCELLED, true",
        "LIVE, SCHEDULED, false",
        "LIVE, LIVE, false",
        "ENDED, SCHEDULED, false",
        "ENDED, LIVE, false",
        "ENDED, CANCELLED, false",
        "ENDED, ENDED, false",
        "CANCELLED, SCHEDULED, false",
        "CANCELLED, LIVE, false",
        "CANCELLED, ENDED, false",
        "CANCELLED, CANCELLED, false"
    })
    void shouldAllowOnlyTheseMoves(MeetingStatus from, MeetingStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }
}
