package com.corridor.office.spaces.application;

import com.corridor.office.spaces.domain.Floor;
import java.util.UUID;

public record FloorSummary(UUID id, int level, String code, String name, String details) {

    static FloorSummary of(Floor floor) {
        return new FloorSummary(floor.id(), floor.level(), floor.code(), floor.name(), floor.details());
    }
}
