package com.corridor.office.spaces.application;

import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.spaces.domain.Floor;
import com.corridor.office.spaces.domain.Room;
import com.corridor.office.spaces.domain.RoomKind;
import com.corridor.office.spaces.events.OfficeAssigned;
import com.corridor.office.spaces.infrastructure.FloorRepository;
import com.corridor.office.spaces.infrastructure.RoomRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HR (and admins) give private offices to people; ordinary desks are claimed by sitting at them. */
@Service
public class OfficeAssignmentService {

    private final RoomRepository rooms;
    private final FloorRepository floors;
    private final ApplicationEventPublisher events;

    OfficeAssignmentService(RoomRepository rooms, FloorRepository floors, ApplicationEventPublisher events) {
        this.rooms = rooms;
        this.floors = floors;
        this.events = events;
    }

    /** Gives the office to {@code ownerId}, or empties it when null; someone else's office must be emptied first. */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public void assign(CurrentUser actor, UUID roomId, UUID ownerId) {
        var office = office(actor, roomId);
        if (ownerId != null && office.ownerId() != null && !office.ownerId().equals(ownerId)) {
            throw new ConflictException("offices.taken");
        }
        if (ownerId != null) {
            rooms.findByOwnerId(ownerId)
                    .filter(other -> !other.id().equals(roomId))
                    .ifPresent(other -> {
                        other.giveTo(null);
                        rooms.saveAndFlush(other);
                    });
        }
        office.giveTo(ownerId);
        rooms.saveAndFlush(office);
        events.publishEvent(new OfficeAssigned(roomId, actor.organizationId(), actor.userId(), office.name(), ownerId));
    }

    /** One private office, where it is, and who has it (null when empty). */
    public record OfficeSlot(UUID id, String name, String place, UUID ownerId) {}

    /** Every private office in the company, for the office picker in the person sheet. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public List<OfficeSlot> list(CurrentUser actor) {
        var offices = rooms.findOffices(actor.organizationId());
        var where = floors
                .findAllById(offices.stream().map(Room::floorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Floor::id, f -> f.building().name() + " · " + f.code()));
        return offices.stream()
                .map(room -> new OfficeSlot(room.id(), room.name(), where.get(room.floorId()), room.ownerId()))
                .toList();
    }

    private Room office(CurrentUser actor, UUID roomId) {
        var room = rooms.findById(roomId)
                .filter(found -> floors.findInOrganization(found.floorId(), actor.organizationId())
                        .isPresent())
                .orElseThrow(() -> new NotFoundException("spaces.roomNotFound"));
        if (room.kind() != RoomKind.OFFICE) {
            throw new ConflictException("spaces.notAnOffice");
        }
        return room;
    }
}
