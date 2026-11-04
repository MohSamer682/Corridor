package com.corridor.office.meetings.application;

import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.meetings.MediaTokens;
import com.corridor.office.meetings.MediaTokens.Join;
import com.corridor.office.meetings.infrastructure.MeetingRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Joining a meeting: invitees only, from 10 minutes before the start until it ends. */
@Service
public class MeetingMedia {

    public static final Duration EARLY = Duration.ofMinutes(10);

    private final MeetingRepository meetings;
    private final MediaTokens tokens;
    private final UserDirectory directory;
    private final Clock clock;

    MeetingMedia(MeetingRepository meetings, MediaTokens tokens, UserDirectory directory, Clock clock) {
        this.meetings = meetings;
        this.tokens = tokens;
        this.directory = directory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Join join(CurrentUser person, UUID meetingId) {
        var meeting = meetings.findInOrganization(meetingId, person.organizationId())
                .orElseThrow(() -> new NotFoundException("meetings.notFound"));
        if (!meeting.invites(person.userId())) {
            throw new ForbiddenException("meetings.notInvited");
        }
        var now = clock.instant();
        if (!meeting.status().isOpen() || !now.isBefore(meeting.endsAt())) {
            throw new ConflictException("meetings.closed");
        }
        var wait = Duration.between(now, meeting.startsAt());
        if (wait.compareTo(EARLY) > 0) {
            throw new ConflictException("meetings.notYet", wait.toMinutes() + 1);
        }
        var name = directory
                .find(person.userId())
                .map(UserDirectory.Person::displayName)
                .orElse("");
        return tokens.issue("meeting-" + meeting.id(), person.userId(), name, meeting.endsAt());
    }
}
