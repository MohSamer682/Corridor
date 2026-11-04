package com.corridor.office.conversations.domain;

/** On the open floor you hear anyone within three tiles, diagonals included. */
public enum OpenFloorRange implements HearingRange {
    INSTANCE;

    public static final int TILES = 3;

    @Override
    public boolean hears(Spot a, Spot b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y())) <= TILES;
    }
}
