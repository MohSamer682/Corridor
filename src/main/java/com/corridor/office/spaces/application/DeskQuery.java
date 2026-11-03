package com.corridor.office.spaces.application;

import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.spaces.domain.Room;
import com.corridor.office.spaces.domain.SeatKind;
import com.corridor.office.spaces.infrastructure.RoomRepository;
import com.corridor.office.spaces.infrastructure.SeatRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeskQuery {

    private final SeatRepository seats;
    private final RoomRepository rooms;

    DeskQuery(SeatRepository seats, RoomRepository rooms) {
        this.seats = seats;
        this.rooms = rooms;
    }

    /** A private office wins over a claimed desk; the office's desk chair is where people go. */
    public Optional<MyDesk> mine(CurrentUser person) {
        var office = rooms.findByOwnerId(person.userId()).flatMap(this::officeDesk);
        if (office.isPresent()) {
            return office;
        }
        return seats.findByAssignedUserId(person.userId())
                .map(seat -> new MyDesk(
                        seat.floorId(),
                        seat.tile().x(),
                        seat.tile().y(),
                        "DESK",
                        seat.roomId() == null
                                ? null
                                : rooms.findById(seat.roomId()).map(Room::name).orElse(null)));
    }

    private Optional<MyDesk> officeDesk(Room room) {
        return seats.findByFloorId(room.floorId()).stream()
                .filter(seat -> room.id().equals(seat.roomId()) && seat.kind() == SeatKind.DESK)
                .findFirst()
                .map(seat ->
                        new MyDesk(room.floorId(), seat.tile().x(), seat.tile().y(), "OFFICE", room.name()));
    }
}
