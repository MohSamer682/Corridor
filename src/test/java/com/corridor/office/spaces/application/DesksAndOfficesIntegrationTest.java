package com.corridor.office.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.spaces.SpaceCatalog;
import com.corridor.office.spaces.TileGrid.Tile;
import com.corridor.office.spaces.domain.RoomKind;
import com.corridor.office.spaces.domain.SeatKind;
import com.corridor.office.spaces.infrastructure.RoomRepository;
import com.corridor.office.spaces.infrastructure.SeatRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true"
        })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DesksAndOfficesIntegrationTest {

    @Autowired
    SpaceCatalog catalog;

    @Autowired
    OfficeAssignmentService offices;

    @Autowired
    DeskQuery desks;

    @Autowired
    CampusQuery campus;

    @Autowired
    SeatRepository seats;

    @Autowired
    RoomRepository rooms;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private UUID organizationId;
    private UUID floorId;
    private CurrentUser nour;
    private CurrentUser omar;
    private CurrentUser hr;

    @BeforeEach
    void pickTheEngineeringFloor() {
        jdbc.sql("UPDATE seats SET assigned_user_id = NULL").update();
        jdbc.sql("UPDATE rooms SET owner_id = NULL").update();
        var n = users.findByEmail("nour@corridor.local").orElseThrow();
        var o = users.findByEmail("omar@corridor.local").orElseThrow();
        organizationId = n.organization().id();
        nour = new CurrentUser(n.id(), organizationId, Role.EMPLOYEE);
        omar = new CurrentUser(o.id(), organizationId, Role.EMPLOYEE);
        hr = new CurrentUser(
                users.findByEmail("admin@corridor.local").orElseThrow().id(), organizationId, Role.HR);
        floorId = campus.campus(nour).buildings().getFirst().floors().get(1).id();
    }

    private Tile freeDesk(int skip) {
        return seats.findByFloorId(floorId).stream()
                .filter(seat -> seat.kind() == SeatKind.DESK)
                .filter(seat -> seat.roomId() == null
                        || rooms.findById(seat.roomId()).orElseThrow().kind() != RoomKind.OFFICE)
                .skip(skip)
                .findFirst()
                .orElseThrow()
                .tile();
    }

    private UUID officeId() {
        return rooms.findByFloorIdOrderByName(floorId).stream()
                .filter(room -> room.kind() == RoomKind.OFFICE)
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Test
    void shouldGiveAFreeDeskToWhoeverSitsThere() {
        var desk = freeDesk(0);

        catalog.sat(organizationId, floorId, nour.userId(), desk);

        assertThat(desks.mine(nour)).hasValueSatisfying(mine -> {
            assertThat(mine.x()).isEqualTo(desk.x());
            assertThat(mine.y()).isEqualTo(desk.y());
            assertThat(mine.kind()).isEqualTo("DESK");
        });
    }

    @Test
    void shouldMoveTheClaimWhenSomeoneSitsAtAnotherDesk() {
        catalog.sat(organizationId, floorId, nour.userId(), freeDesk(0));
        var second = freeDesk(1);

        catalog.sat(organizationId, floorId, nour.userId(), second);

        assertThat(desks.mine(nour))
                .hasValueSatisfying(mine -> assertThat(mine.x()).isEqualTo(second.x()));
        assertThat(seats.findByFloorId(floorId).stream()
                        .filter(seat -> nour.userId().equals(seat.assignedUserId())))
                .hasSize(1);
    }

    @Test
    void shouldKeepSomeoneElsesDesk() {
        var desk = freeDesk(0);
        catalog.sat(organizationId, floorId, nour.userId(), desk);

        catalog.sat(organizationId, floorId, omar.userId(), desk);

        assertThat(desks.mine(omar)).isEmpty();
        assertThat(desks.mine(nour)).isPresent();
    }

    @Test
    void shouldNotClaimSofasOrEmptyFloor() {
        var sofa = seats.findByFloorId(floorId).stream()
                .filter(seat -> seat.kind() == SeatKind.SOFA)
                .findFirst()
                .orElseThrow()
                .tile();

        catalog.sat(organizationId, floorId, nour.userId(), sofa);
        catalog.sat(organizationId, floorId, nour.userId(), new Tile(26, 22));

        assertThat(desks.mine(nour)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldLetHrGiveSomeoneAnOffice() {
        var office = officeId();

        offices.assign(hr, office, nour.userId());

        assertThat(rooms.findById(office).orElseThrow().ownerId()).isEqualTo(nour.userId());
        assertThat(desks.mine(nour))
                .hasValueSatisfying(mine -> assertThat(mine.kind()).isEqualTo("OFFICE"));
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE action = 'room.owner_assigned'")
                        .query(Long.class)
                        .single())
                .isPositive();
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldRefuseAnOfficeThatAlreadyHasSomeone() {
        var office = officeId();
        offices.assign(hr, office, nour.userId());

        assertThatThrownBy(() -> offices.assign(hr, office, omar.userId())).isInstanceOf(ConflictException.class);

        offices.assign(hr, office, null);
        offices.assign(hr, office, omar.userId());
        assertThat(rooms.findById(office).orElseThrow().ownerId()).isEqualTo(omar.userId());
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldListEveryOfficeWithWhereItIsAndWhoHasIt() {
        var office = officeId();
        offices.assign(hr, office, nour.userId());

        var list = offices.list(hr);

        assertThat(list).isNotEmpty();
        assertThat(list)
                .filteredOn(slot -> slot.id().equals(office))
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.ownerId()).isEqualTo(nour.userId());
                    assertThat(slot.place()).contains("L1");
                });
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldNotLetManagersAssignOffices() {
        var manager = new CurrentUser(UUID.randomUUID(), organizationId, Role.MANAGER);

        assertThatThrownBy(() -> offices.assign(manager, officeId(), nour.userId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
