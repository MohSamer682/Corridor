package com.corridor.office.spaces;

import static org.assertj.core.api.Assertions.assertThat;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.config.JpaAuditingConfig;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.spaces.domain.Building;
import com.corridor.office.spaces.domain.Floor;
import com.corridor.office.spaces.domain.TileGrid;
import com.corridor.office.spaces.domain.TileGrid.Tile;
import com.corridor.office.spaces.infrastructure.BuildingRepository;
import com.corridor.office.spaces.infrastructure.FloorLayoutRepository;
import com.corridor.office.spaces.infrastructure.SeatRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true"
        })
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SeededCampusIntegrationTest {

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    BuildingRepository buildings;

    @Autowired
    FloorLayoutRepository layouts;

    @Autowired
    SeatRepository seats;

    private List<Building> campus() {
        var organization = organizations.findFirstByOrderByCreatedAtAsc().orElseThrow();
        return buildings.findCampus(organization.id());
    }

    private List<Floor> allFloors() {
        return campus().stream().flatMap(building -> building.floors().stream()).toList();
    }

    @Test
    void shouldSeedTwoBuildingsWithFourFloors() {
        var campus = campus();

        assertThat(campus).extracting(Building::name).containsExactly("Corridor HQ", "Innovation Lab");
        assertThat(campus.get(0).floors()).extracting(Floor::code).containsExactly("G", "L1", "L2");
        assertThat(campus.get(1).floors()).extracting(Floor::code).containsExactly("G");
    }

    @Test
    void shouldReachEverySeatFromTheArrivalTile() {
        var unreachable = new ArrayList<String>();
        for (var floor : allFloors()) {
            var grid = layouts.findByFloorIdAndVersion(floor.id(), floor.currentLayoutVersion())
                    .orElseThrow()
                    .grid();
            for (var seat : seats.findByFloorId(floor.id())) {
                if (!reachable(grid, seat.tile())) {
                    unreachable.add(floor.name() + " seat " + seat.tile());
                }
            }
        }

        assertThat(unreachable).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReachEveryHotSpotFromTheArrivalTile() {
        var unreachable = new ArrayList<String>();
        for (var floor : allFloors()) {
            var layout = layouts.findByFloorIdAndVersion(floor.id(), floor.currentLayoutVersion())
                    .orElseThrow();
            var hotSpots = (Map<String, Map<String, Number>>) layout.furniture().get("hotSpots");
            hotSpots.forEach((name, area) -> {
                var corner = new Tile(area.get("x0").intValue(), area.get("y0").intValue());
                if (!reachable(layout.grid(), corner)) {
                    unreachable.add(floor.name() + " hot spot " + name);
                }
            });
        }

        assertThat(unreachable).isEmpty();
    }

    @Test
    void shouldPlaceEverySeatOnAWalkableTile() {
        for (var floor : allFloors()) {
            var grid = layouts.findByFloorIdAndVersion(floor.id(), floor.currentLayoutVersion())
                    .orElseThrow()
                    .grid();
            assertThat(seats.findByFloorId(floor.id()))
                    .as(floor.name())
                    .allSatisfy(seat -> assertThat(grid.isWalkable(seat.tile())).isTrue());
        }
    }

    private static boolean reachable(TileGrid grid, Tile tile) {
        return tile.equals(grid.arrival()) || !grid.path(grid.arrival(), tile).isEmpty();
    }
}
