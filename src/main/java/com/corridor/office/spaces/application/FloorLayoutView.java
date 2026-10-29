package com.corridor.office.spaces.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tiles are one character per tile, row by row; furniture is the decor the 3D view draws. */
public record FloorLayoutView(
        UUID floorId,
        int version,
        int width,
        int height,
        Position arrival,
        String tiles,
        Map<String, Object> furniture,
        List<RoomView> rooms,
        List<SeatView> seats) {

    public record Position(int x, int y) {}
}
