package com.corridor.office.meetings.domain;

import com.corridor.office.common.domain.ConflictException;

/** The room is taken for part of that time; says by which meeting. */
public class RoomAlreadyBookedException extends ConflictException {

    public RoomAlreadyBookedException(String roomName, String otherTitle) {
        super("meetings.roomBooked", roomName, otherTitle);
    }
}
