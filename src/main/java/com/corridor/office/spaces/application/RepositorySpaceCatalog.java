package com.corridor.office.spaces.application;

import com.corridor.office.spaces.SpaceCatalog;
import com.corridor.office.spaces.TileGrid;
import com.corridor.office.spaces.infrastructure.FloorLayoutRepository;
import com.corridor.office.spaces.infrastructure.FloorRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class RepositorySpaceCatalog implements SpaceCatalog {

    private final FloorRepository floors;
    private final FloorLayoutRepository layouts;

    RepositorySpaceCatalog(FloorRepository floors, FloorLayoutRepository layouts) {
        this.floors = floors;
        this.layouts = layouts;
    }

    @Override
    public Optional<TileGrid> grid(UUID organizationId, UUID floorId) {
        return floors.findInOrganization(floorId, organizationId)
                .flatMap(floor -> layouts.findByFloorIdAndVersion(floorId, floor.currentLayoutVersion()))
                .map(layout -> layout.grid());
    }
}
