package com.corridor.office.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Organization;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.TeamRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class TeamServiceIntegrationTest {

    @Autowired
    TeamService teamService;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    TeamRepository teams;

    @Autowired
    JdbcClient jdbc;

    private Organization organization;
    private CurrentUser actor;

    @BeforeEach
    void createOrganization() {
        organization = organizations.save(IdentityFixtures.organization());
        actor = new CurrentUser(UUID.randomUUID(), organization.id(), Role.ADMIN);
    }

    @AfterEach
    void clearTables() {
        jdbc.sql("truncate users, teams, organizations cascade").update();
    }

    @Test
    void shouldListTeamsWithMemberCounts() {
        var design = teamService.create(actor, "Design", "#23395d");
        teamService.create(actor, "Platform", "#2F7D6D");
        var user = User.register(organization, "a@corridor.local", "A", Role.EMPLOYEE, "{noop}x");
        user.moveToTeam(teams.findById(design.id()).orElseThrow());
        users.save(user);

        var page = teamService.list(actor, PageRequest.of(0, 20));

        assertThat(page.items()).extracting(TeamView::name).containsExactly("Design", "Platform");
        assertThat(page.items()).extracting(TeamView::memberCount).containsExactly(1L, 0L);
        assertThat(page.items().getFirst().color()).isEqualTo("#23395D");
    }

    @Test
    void shouldRejectADuplicateTeamNameIgnoringCase() {
        teamService.create(actor, "Design", "#23395D");

        assertThatThrownBy(() -> teamService.create(actor, " design ", "#2F7D6D"))
                .hasMessage("teams.duplicate");
    }

    @Test
    void shouldRefuseToDeleteATeamWithMembers() {
        var design = teamService.create(actor, "Design", "#23395D");
        var user = User.register(organization, "a@corridor.local", "A", Role.EMPLOYEE, "{noop}x");
        user.moveToTeam(teams.findById(design.id()).orElseThrow());
        users.save(user);

        assertThatThrownBy(() -> teamService.delete(actor, design.id())).hasMessage("teams.notEmpty");
    }

    @Test
    void shouldRejectAStaleTeamVersion() {
        var design = teamService.create(actor, "Design", "#23395D");
        teamService.update(actor, design.id(), design.version(), "Product design", "#23395D");

        assertThatThrownBy(() -> teamService.update(actor, design.id(), design.version(), "Again", "#23395D"))
                .hasMessage("teams.stale");
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void shouldLetEveryoneListButOnlyAdminsCreate() {
        assertThat(teamService.list(actor, PageRequest.of(0, 20)).items()).isEmpty();
        assertThatThrownBy(() -> teamService.create(actor, "Design", "#23395D"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
