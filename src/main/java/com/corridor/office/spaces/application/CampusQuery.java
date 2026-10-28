package com.corridor.office.spaces.application;

import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.spaces.application.FloorLayoutView.Position;
import com.corridor.office.spaces.domain.Floor;
import com.corridor.office.spaces.domain.Seat;
import com.corridor.office.spaces.infrastructure.BuildingRepository;
import com.corridor.office.spaces.infrastructure.FloorLayoutRepository;
import com.corridor.office.spaces.infrastructure.FloorRepository;
import com.corridor.office.spaces.infrastructure.RoomRepository;
import com.corridor.office.spaces.infrastructure.SeatRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CampusQuery {

    private final BuildingRepository buildings;
    private final FloorRepository floors;
    private final FloorLayoutRepository layouts;
    private final RoomRepository rooms;
    private final SeatRepository seats;

    CampusQuery(
            BuildingRepository buildings,
            FloorRepository floors,
            FloorLayoutRepository layouts,
            RoomRepository rooms,
            SeatRepository seats) {
        this.buildings = buildings;
        this.floors = floors;
        this.layouts = layouts;
        this.rooms = rooms;
        this.seats = seats;
    }

    public CampusView campus(CurrentUser viewer) {
        return new CampusView(buildings.findCampus(viewer.organizationId()).stream()
                .map(BuildingView::of)
                .toList());
    }

    public int layoutVersion(CurrentUser viewer, UUID floorId) {
        return floor(viewer, floorId).currentLayoutVersion();
    }

    public FloorLayoutView layout(CurrentUser viewer, UUID floorId) {
        var floor = floor(viewer, floorId);
        var layout = layouts.findByFloorIdAndVersion(floorId, floor.currentLayoutVersion())
                .orElseThrow(() -> new NotFoundException("spaces.floorNotFound"));
        var floorSeats = seats.findByFloorId(floorId);
        var grid = layout.grid();
        return new FloorLayoutView(
                floorId,
                layout.version(),
                grid.width(),
                grid.height(),
                new Position(grid.arrival().x(), grid.arrival().y()),
                layout.tiles(),
                layout.furniture(),
                roomViews(floorId, floorSeats),
                floorSeats.stream().map(SeatView::of).toList());
    }

    public List<RoomView> rooms(CurrentUser viewer, UUID floorId) {
        floor(viewer, floorId);
        return roomViews(floorId, seats.findByFloorId(floorId));
    }

    private List<RoomView> roomViews(UUID floorId, List<Seat> floorSeats) {
        var seatsPerRoom = floorSeats.stream()
                .map(Seat::roomId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return rooms.findByFloorIdOrderByName(floorId).stream()
                .map(room -> RoomView.of(
                        room, seatsPerRoom.getOrDefault(room.id(), 0L).intValue()))
                .toList();
    }

    private Floor floor(CurrentUser viewer, UUID floorId) {
        return floors.findInOrganization(floorId, viewer.organizationId())
                .orElseThrow(() -> new NotFoundException("spaces.floorNotFound"));
    }
}
