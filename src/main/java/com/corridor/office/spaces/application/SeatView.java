package com.corridor.office.spaces.application;

import com.corridor.office.spaces.domain.Facing;
import com.corridor.office.spaces.domain.Seat;
import com.corridor.office.spaces.domain.SeatKind;
import java.util.UUID;

public record SeatView(UUID id, UUID roomId, int x, int y, Facing facing, SeatKind kind, UUID assignedUserId) {

    static SeatView of(Seat seat) {
        return new SeatView(
                seat.id(),
                seat.roomId(),
                seat.tile().x(),
                seat.tile().y(),
                seat.facing(),
                seat.kind(),
                seat.assignedUserId());
    }
}
