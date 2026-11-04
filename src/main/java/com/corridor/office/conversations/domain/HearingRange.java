package com.corridor.office.conversations.domain;

/** Whether two people can hear each other where they stand. */
public interface HearingRange {

    boolean hears(Spot a, Spot b);

    /** A private room shuts out everyone else, so it decides whenever one of the two is inside one. */
    static HearingRange between(Spot a, Spot b) {
        return a.privateRoomId() != null || b.privateRoomId() != null
                ? PrivateRoomRange.INSTANCE
                : OpenFloorRange.INSTANCE;
    }
}
