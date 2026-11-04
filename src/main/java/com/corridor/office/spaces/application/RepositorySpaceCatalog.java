package com.corridor.office.spaces.application;

import com.corridor.office.spaces.SpaceCatalog;
import com.corridor.office.spaces.TileGrid;
import com.corridor.office.spaces.TileGrid.Tile;
import com.corridor.office.spaces.domain.Floor;
import com.corridor.office.spaces.domain.Room;
import com.corridor.office.spaces.domain.RoomKind;
import com.corridor.office.spaces.domain.Seat;
import com.corridor.office.spaces.domain.SeatKind;
import com.corridor.office.spaces.infrastructure.FloorLayoutRepository;
import com.corridor.office.spaces.infrastructure.FloorRepository;
import com.corridor.office.spaces.infrastructure.RoomRepository;
import com.corridor.office.spaces.infrastructure.SeatRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RepositorySpaceCatalog implements SpaceCatalog {

    private final FloorRepository floors;
    private final FloorLayoutRepository layouts;
    private final SeatRepository seats;
    private final RoomRepository rooms;

    RepositorySpaceCatalog(
            FloorRepository floors, FloorLayoutRepository layouts, SeatRepository seats, RoomRepository rooms) {
        this.floors = floors;
        this.layouts = layouts;
        this.seats = seats;
        this.rooms = rooms;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TileGrid> grid(UUID organizationId, UUID floorId) {
        return floors.findInOrganization(floorId, organizationId)
                .flatMap(floor -> layouts.findByFloorIdAndVersion(floorId, floor.currentLayoutVersion()))
                .map(layout -> layout.grid());
    }

    @Override
    @Transactional
    public void sat(UUID organizationId, UUID floorId, UUID userId, Tile tile) {
        if (floors.findInOrganization(floorId, organizationId).isEmpty()) {
            return;
        }
        seats.findByFloorIdAndXAndY(floorId, (short) tile.x(), (short) tile.y())
                .filter(seat -> seat.kind() == SeatKind.DESK && seat.assignedUserId() == null)
                .filter(seat -> !inPrivateOffice(seat))
                .ifPresent(desk -> {
                    seats.findByAssignedUserId(userId).ifPresent(previous -> {
                        previous.release();
                        seats.saveAndFlush(previous);
                    });
                    desk.assignTo(userId);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrivateRoom> privateRooms(UUID organizationId, UUID floorId) {
        if (floors.findInOrganization(floorId, organizationId).isEmpty()) {
            return List.of();
        }
        return rooms.findByFloorIdOrderByName(floorId).stream()
                .filter(room -> room.isPrivate())
                .map(room ->
                        new PrivateRoom(room.id(), room.name(), room.minX(), room.minY(), room.maxX(), room.maxY()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookableRoom> bookableRooms(UUID organizationId) {
        var rooms = this.rooms.findBookable(organizationId);
        var places = floors
                .findAllById(rooms.stream().map(Room::floorId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Floor::id, f -> f.building().name() + " \u00b7 " + f.code()));
        return rooms.stream()
                .map(room -> new BookableRoom(room.id(), room.name(), room.floorId(), places.get(room.floorId())))
                .toList();
    }

    // Office desks belong to whoever HR gave the office to, so sitting there claims nothing.
    private boolean inPrivateOffice(Seat seat) {
        return seat.roomId() != null
                && rooms.findById(seat.roomId())
                        .map(room -> room.kind() == RoomKind.OFFICE)
                        .orElse(false);
    }
}
