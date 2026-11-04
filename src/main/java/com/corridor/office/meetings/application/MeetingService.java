package com.corridor.office.meetings.application;

import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.idempotency.IdempotencyGuard;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.identity.UserDirectory.Member;
import com.corridor.office.meetings.domain.Meeting;
import com.corridor.office.meetings.domain.MeetingSlot;
import com.corridor.office.meetings.domain.MeetingStatus;
import com.corridor.office.meetings.domain.RoomAlreadyBookedException;
import com.corridor.office.meetings.events.MeetingCancelled;
import com.corridor.office.meetings.infrastructure.MeetingRepository;
import com.corridor.office.spaces.SpaceCatalog;
import com.corridor.office.spaces.SpaceCatalog.BookableRoom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Booking rooms for meetings. A quick look for a clash gives the friendly message; the exclusion constraint is what
 * actually stops two bookings racing for one slot (ADR-007).
 */
@Service
public class MeetingService {

    /** What the screen sends to book or change a meeting. */
    public record ScheduleMeeting(UUID roomId, String title, Instant startsAt, int minutes, List<UUID> invitees) {}

    public record MeetingView(
            UUID id,
            String title,
            UUID roomId,
            String roomName,
            String place,
            UUID floorId,
            Instant startsAt,
            Instant endsAt,
            MeetingStatus status,
            UUID organizerId,
            List<UUID> attendees) {}

    private final MeetingRepository meetings;
    private final SpaceCatalog spaces;
    private final MeetingContext context;

    MeetingService(MeetingRepository meetings, SpaceCatalog spaces, MeetingContext context) {
        this.meetings = meetings;
        this.spaces = spaces;
        this.context = context;
    }

    public MeetingView schedule(CurrentUser organizer, ScheduleMeeting request, UUID key) {
        var rooms = rooms(organizer);
        var slot = slot(organizer, request, rooms);
        try {
            return context.tx()
                    .execute(status -> context.guard().once(organizer.userId(), key, MeetingView.class, () -> {
                        refuseClash(slot, null, rooms);
                        var meeting = meetings.saveAndFlush(
                                Meeting.schedule(organizer.organizationId(), organizer.userId(), slot));
                        return view(meeting, rooms);
                    }));
        } catch (DataIntegrityViolationException race) {
            throw clashFor(slot, null, rooms, race);
        }
    }

    public MeetingView reschedule(CurrentUser organizer, UUID meetingId, ScheduleMeeting request) {
        var rooms = rooms(organizer);
        var slot = slot(organizer, request, rooms);
        try {
            return context.tx().execute(status -> {
                var meeting = organizedBy(organizer, meetingId);
                refuseClash(slot, meetingId, rooms);
                meeting.reschedule(slot);
                return view(meetings.saveAndFlush(meeting), rooms);
            });
        } catch (DataIntegrityViolationException race) {
            throw clashFor(slot, meetingId, rooms, race);
        }
    }

    public void cancel(CurrentUser organizer, UUID meetingId) {
        var rooms = rooms(organizer);
        context.tx().executeWithoutResult(status -> {
            var meeting = organizedBy(organizer, meetingId);
            meeting.moveTo(MeetingStatus.CANCELLED);
            meetings.saveAndFlush(meeting);
            var room = rooms.get(meeting.roomId());
            context.events()
                    .publishEvent(new MeetingCancelled(
                            meeting.id(),
                            meeting.organizationId(),
                            organizer.userId(),
                            meeting.title(),
                            room == null ? "" : room.name(),
                            meeting.startsAt()));
        });
    }

    /** Your meetings (organized or invited) that overlap [from, to). */
    public List<MeetingView> list(CurrentUser viewer, Instant from, Instant to) {
        var rooms = rooms(viewer);
        return context.readOnly()
                .execute(status -> meetings.forPerson(viewer.organizationId(), viewer.userId(), from, to).stream()
                        .map(m -> view(m, rooms))
                        .toList());
    }

    /** Everything booked in one room in [from, to), so the form can show what is free. */
    public List<MeetingView> inRoom(CurrentUser viewer, UUID roomId, Instant from, Instant to) {
        var rooms = rooms(viewer);
        if (!rooms.containsKey(roomId)) {
            throw new NotFoundException("meetings.roomNotBookable");
        }
        return context.readOnly().execute(status -> meetings.inRoom(viewer.organizationId(), roomId, from, to).stream()
                .map(m -> view(m, rooms))
                .toList());
    }

    public List<BookableRoom> bookableRooms(CurrentUser viewer) {
        return spaces.bookableRooms(viewer.organizationId());
    }

    /** A meeting you organize or are invited to; anyone else gets 404, as if it weren't there. */
    public Meeting visibleTo(CurrentUser viewer, UUID meetingId) {
        return context.readOnly().execute(status -> meetings.findInOrganization(meetingId, viewer.organizationId())
                .filter(m -> m.invites(viewer.userId()))
                .orElseThrow(() -> new NotFoundException("meetings.notFound")));
    }

    private MeetingSlot slot(CurrentUser organizer, ScheduleMeeting request, Map<UUID, BookableRoom> rooms) {
        if (request.roomId() == null || !rooms.containsKey(request.roomId())) {
            throw new NotFoundException("meetings.roomNotBookable");
        }
        var slot = MeetingSlot.of(
                new MeetingSlot.Draft(
                        request.roomId(), request.title(), request.startsAt(), request.minutes(), request.invitees()),
                context.clock().instant());
        var colleagues = context.directory().members(organizer.organizationId(), null).stream()
                .map(Member::userId)
                .collect(Collectors.toSet());
        if (!colleagues.containsAll(slot.invitees())) {
            throw new InvalidInputException("meetings.invitees");
        }
        return slot;
    }

    private Meeting organizedBy(CurrentUser organizer, UUID meetingId) {
        var meeting = meetings.findInOrganization(meetingId, organizer.organizationId())
                .orElseThrow(() -> new NotFoundException("meetings.notFound"));
        if (!meeting.organizerId().equals(organizer.userId())) {
            throw new ForbiddenException("meetings.notOrganizer");
        }
        return meeting;
    }

    private void refuseClash(MeetingSlot slot, UUID except, Map<UUID, BookableRoom> rooms) {
        var clash = meetings.clashes(
                slot.roomId(), slot.startsAt(), slot.endsAt(), except == null ? new UUID(0, 0) : except);
        if (!clash.isEmpty()) {
            throw new RoomAlreadyBookedException(
                    rooms.get(slot.roomId()).name(), clash.getFirst().title());
        }
    }

    // The constraint won a race the quick look missed; name the meeting that got there first.
    private RuntimeException clashFor(
            MeetingSlot slot, UUID except, Map<UUID, BookableRoom> rooms, RuntimeException race) {
        var winner = context.readOnly()
                .execute(status -> meetings.clashes(
                        slot.roomId(), slot.startsAt(), slot.endsAt(), except == null ? new UUID(0, 0) : except));
        if (winner == null || winner.isEmpty()) {
            return race;
        }
        return new RoomAlreadyBookedException(
                rooms.get(slot.roomId()).name(), winner.getFirst().title());
    }

    private Map<UUID, BookableRoom> rooms(CurrentUser viewer) {
        return spaces.bookableRooms(viewer.organizationId()).stream()
                .collect(Collectors.toMap(BookableRoom::id, Function.identity()));
    }

    private static MeetingView view(Meeting m, Map<UUID, BookableRoom> rooms) {
        var room = rooms.get(m.roomId());
        return new MeetingView(
                m.id(),
                m.title(),
                m.roomId(),
                room == null ? "" : room.name(),
                room == null ? "" : room.place(),
                room == null ? null : room.floorId(),
                m.startsAt(),
                m.endsAt(),
                m.status(),
                m.organizerId(),
                m.attendees().stream().sorted().toList());
    }

    /** The plumbing the service needs, in one place so its constructor stays short. */
    record MeetingContext(
            TransactionTemplate tx,
            TransactionTemplate readOnly,
            IdempotencyGuard guard,
            UserDirectory directory,
            ApplicationEventPublisher events,
            Clock clock) {}
}
