package com.corridor.office.conversations.domain;

/** Inside a private room you hear the room, and nobody outside it hears in. */
public enum PrivateRoomRange implements HearingRange {
    INSTANCE;

    @Override
    public boolean hears(Spot a, Spot b) {
        return a.privateRoomId() != null && a.privateRoomId().equals(b.privateRoomId());
    }
}
