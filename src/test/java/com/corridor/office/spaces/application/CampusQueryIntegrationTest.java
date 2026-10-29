package com.corridor.office.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true",
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CampusQueryIntegrationTest {

    @Autowired
    CampusQuery campus;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private CurrentUser employee;

    @BeforeEach
    void signInAsAnEmployee() {
        var organizationId =
                organizations.findFirstByOrderByCreatedAtAsc().orElseThrow().id();
        employee = new CurrentUser(UUID.randomUUID(), organizationId, Role.EMPLOYEE);
    }

    private UUID engineeringFloor() {
        return campus.campus(employee).buildings().getFirst().floors().get(1).id();
    }

    @Test
    void shouldReadTheWholeCampusInOneQuery() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var view = campus.campus(employee);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(view.buildings()).extracting(BuildingView::name).containsExactly("Corridor HQ", "Innovation Lab");
        assertThat(view.buildings().getFirst().floors())
                .extracting(FloorSummary::name)
                .containsExactly("Lobby & Commons", "Engineering", "Executive & Product");
    }

    @Test
    void shouldServeTheLayoutWithRoomsAndSeats() {
        var layout = campus.layout(employee, engineeringFloor());

        assertThat(layout.width()).isEqualTo(56);
        assertThat(layout.height()).isEqualTo(36);
        assertThat(layout.tiles()).hasSize(56 * 36);
        assertThat(layout.arrival()).isEqualTo(new FloorLayoutView.Position(26, 22));
        assertThat(layout.furniture()).containsKeys("items", "hotSpots");
        assertThat(layout.rooms()).extracting(RoomView::key).contains("nile", "pyr", "luxor");
        assertThat(layout.seats()).hasSize(118);
    }

    @Test
    void shouldCountSeatsAsRoomCapacity() {
        var nile = campus.rooms(employee, engineeringFloor()).stream()
                .filter(room -> room.key().equals("nile"))
                .findFirst()
                .orElseThrow();

        assertThat(nile.name()).isEqualTo("Nile Room");
        assertThat(nile.capacity()).isEqualTo(12);
    }

    @Test
    void shouldNotOpenAFloorOfAnotherCompany() {
        var floorId = engineeringFloor();
        var stranger = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> campus.layout(stranger, floorId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> campus.rooms(stranger, floorId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> campus.layoutVersion(stranger, floorId)).isInstanceOf(NotFoundException.class);
        assertThat(campus.campus(stranger).buildings()).isEmpty();
    }
}
