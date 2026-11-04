package com.corridor.office.meetings.domain;

import com.corridor.office.common.domain.AuditedEntity;
import com.corridor.office.common.domain.ConflictException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class Meeting extends AuditedEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID roomId;

    @Column(nullable = false, updatable = false)
    private UUID organizerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeetingStatus status;

    @Version
    private long version;

    @ElementCollection
    @CollectionTable(name = "meeting_attendees", joinColumns = @JoinColumn(name = "meeting_id"))
    @Column(name = "user_id")
    private Set<UUID> attendees = new HashSet<>();

    protected Meeting() {}

    private Meeting(UUID organizationId, UUID organizerId, MeetingSlot slot) {
        this.organizationId = organizationId;
        this.organizerId = organizerId;
        this.status = MeetingStatus.SCHEDULED;
        apply(slot);
    }

    /** A new meeting; the slot has already passed {@link MeetingSlot}'s rules. */
    public static Meeting schedule(UUID organizationId, UUID organizerId, MeetingSlot slot) {
        return new Meeting(organizationId, organizerId, slot);
    }

    public void reschedule(MeetingSlot slot) {
        if (!status.isOpen()) {
            throw new ConflictException("meetings.closed");
        }
        apply(slot);
    }

    public void moveTo(MeetingStatus next) {
        if (!status.canMoveTo(next)) {
            throw new ConflictException("meetings.closed");
        }
        status = next;
    }

    private void apply(MeetingSlot slot) {
        roomId = slot.roomId();
        title = slot.title();
        startsAt = slot.startsAt();
        endsAt = slot.endsAt();
        attendees.clear();
        attendees.addAll(slot.invitees());
        attendees.remove(organizerId);
    }

    public boolean invites(UUID userId) {
        return organizerId.equals(userId) || attendees.contains(userId);
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID roomId() {
        return roomId;
    }

    public UUID organizerId() {
        return organizerId;
    }

    public String title() {
        return title;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public MeetingStatus status() {
        return status;
    }

    public Collection<UUID> attendees() {
        return Set.copyOf(attendees);
    }
}
