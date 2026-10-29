package com.corridor.office.spaces.application;

import com.corridor.office.spaces.domain.Room;
import com.corridor.office.spaces.domain.RoomKind;
import java.util.UUID;

/** Bounds are inclusive tile coordinates; capacity is the number of seats in the room. */
public record RoomView(
        UUID id,
        String key,
        String name,
        RoomKind kind,
        int minX,
        int minY,
        int maxX,
        int maxY,
        boolean isPrivate,
        UUID ownerId,
        int capacity) {

    static RoomView of(Room room, int capacity) {
        return new RoomView(
                room.id(),
                room.key(),
                room.name(),
                room.kind(),
                room.minX(),
                room.minY(),
                room.maxX(),
                room.maxY(),
                room.isPrivate(),
                room.ownerId(),
                capacity);
    }
}
