package com.corridor.office.spaces.application;

import com.corridor.office.spaces.domain.Building;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BuildingView(
        UUID id, String name, String subtitle, BigDecimal campusX, BigDecimal campusZ, List<FloorSummary> floors) {

    static BuildingView of(Building building) {
        return new BuildingView(
                building.id(),
                building.name(),
                building.subtitle(),
                building.campusX(),
                building.campusZ(),
                building.floors().stream().map(FloorSummary::of).toList());
    }
}
