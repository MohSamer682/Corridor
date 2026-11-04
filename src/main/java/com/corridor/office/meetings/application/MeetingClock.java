package com.corridor.office.meetings.application;

import com.corridor.office.meetings.infrastructure.MeetingRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Moves meetings to LIVE when they start and to ENDED when they finish. */
@Component
public class MeetingClock {

    private final MeetingRepository meetings;
    private final Clock clock;

    MeetingClock(MeetingRepository meetings, Clock clock) {
        this.meetings = meetings;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void tick() {
        var now = clock.instant();
        meetings.end(now);
        meetings.start(now);
    }
}
