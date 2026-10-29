package com.corridor.office.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.config.JpaAuditingConfig;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.spaces.domain.Building;
import com.corridor.office.spaces.domain.Facing;
import com.corridor.office.spaces.domain.Floor;
import com.corridor.office.spaces.domain.RoomKind;
import com.corridor.office.spaces.domain.SeatKind;
import com.corridor.office.spaces.domain.TileGrid.Tile;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SpacesRepositoriesTest {

    private static final String TILES = "CCCCC" + "C...C" + "C##.C" + "CCCCC";

    @Autowired
    BuildingRepository buildings;

    @Autowired
    FloorRepository floors;

    @Autowired
    FloorLayoutRepository layouts;

    @Autowired
    RoomRepository rooms;

    @Autowired
    SeatRepository seats;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager entityManager;

    private UUID organizationId;
    private UUID floorId;

    @BeforeEach
    void seedOneFloor() {
        organizationId =
                organizations.saveAndFlush(IdentityFixtures.organization()).id();
        var buildingId = insertBuilding("Corridor HQ", 1);
        floorId = insertFloor(buildingId, 0, "G");
        insertFloor(buildingId, 1, "L1");
        jdbc.sql(
                        """
                        INSERT INTO floor_layouts (id, floor_id, version, width, height, arrival_x, arrival_y,
                                                   tiles, furniture, created_at)
                        VALUES (gen_random_uuid(), ?, 1, 5, 4, 1, 1, ?, '{"items": []}', now())
                        """)
                .params(floorId, TILES)
                .update();
        jdbc.sql(
                        """
                        INSERT INTO rooms (id, floor_id, key, name, kind, min_x, min_y, max_x, max_y, is_private,
                                           created_at, updated_at)
                        VALUES (gen_random_uuid(), ?, 'nile', 'Nile Room', 'MEETING', 0, 0, 4, 3, true, now(), now())
                        """)
                .param(floorId)
                .update();
        insertSeat(3, 2);
        entityManager.clear();
    }

    private UUID insertBuilding(String name, int sortOrder) {
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO buildings (id, organization_id, name, campus_x, campus_z, sort_order,
                                               created_at, updated_at)
                        VALUES (?, ?, ?, -30, -12, ?, now(), now())
                        """)
                .params(id, organizationId, name, sortOrder)
                .update();
        return id;
    }

    private UUID insertFloor(UUID buildingId, int level, String code) {
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO floors (id, building_id, level, code, name, current_layout_version,
                                            created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'Floor ' || ?, 1, now(), now())
                        """)
                .params(id, buildingId, level, code, code)
                .update();
        return id;
    }

    private void insertSeat(int x, int y) {
        jdbc.sql(
                        """
                        INSERT INTO seats (id, floor_id, x, y, facing, kind, created_at, updated_at)
                        VALUES (gen_random_uuid(), ?, ?, ?, 'N', 'CHAIR', now(), now())
                        """)
                .params(floorId, x, y)
                .update();
    }

    @Test
    void shouldReadTheCampusWithFloorsInOrder() {
        var campus = buildings.findCampus(organizationId);

        assertThat(campus).extracting(Building::name).containsExactly("Corridor HQ");
        assertThat(campus.getFirst().floors()).extracting(Floor::code).containsExactly("G", "L1");
    }

    @Test
    void shouldNotFindAFloorFromAnotherOrganization() {
        assertThat(floors.findInOrganization(floorId, organizationId)).isPresent();
        assertThat(floors.findInOrganization(floorId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldReadBackALayoutWithItsGridAndDecor() {
        var layout = layouts.findByFloorIdAndVersion(floorId, 1).orElseThrow();

        assertThat(layout.grid().arrival()).isEqualTo(new Tile(1, 1));
        assertThat(layout.grid().isWalkable(new Tile(3, 2))).isTrue();
        assertThat(layout.furniture()).containsKey("items");
    }

    @Test
    void shouldReadRoomsAndSeats() {
        assertThat(rooms.findByFloorIdOrderByName(floorId)).singleElement().satisfies(room -> {
            assertThat(room.kind()).isEqualTo(RoomKind.MEETING);
            assertThat(room.isPrivate()).isTrue();
        });
        assertThat(seats.findByFloorId(floorId)).singleElement().satisfies(seat -> {
            assertThat(seat.tile()).isEqualTo(new Tile(3, 2));
            assertThat(seat.facing()).isEqualTo(Facing.N);
            assertThat(seat.kind()).isEqualTo(SeatKind.CHAIR);
        });
    }

    @Test
    void shouldRejectALayoutWhoseTilesDoNotMatchItsSize() {
        assertThatThrownBy(() -> jdbc.sql(
                                """
                                INSERT INTO floor_layouts (id, floor_id, version, width, height, arrival_x, arrival_y,
                                                           tiles, created_at)
                                VALUES (gen_random_uuid(), ?, 2, 5, 5, 1, 1, ?, now())
                                """)
                        .params(floorId, TILES)
                        .update())
                .hasStackTraceContaining("ck_floor_layouts_size");
    }

    @Test
    void shouldRejectTwoSeatsOnOneTile() {
        assertThatThrownBy(() -> insertSeat(3, 2)).hasStackTraceContaining("uq_seats_floor_id_x_y");
    }
}
