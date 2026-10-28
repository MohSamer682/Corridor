package com.corridor.office.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamTest {

    private final Organization organization = IdentityFixtures.organization();

    @Test
    void shouldStoreTeamColorInUpperCase() {
        var team = Team.create(organization, "Design", "#c9a266");

        assertThat(team.color()).isEqualTo("#C9A266");
    }

    @Test
    void shouldTrimTheNameWhenRenaming() {
        var team = Team.create(organization, "Design", "#23395D");

        team.rename("  Product design ");

        assertThat(team.name()).isEqualTo("Product design");
    }

    @Test
    void shouldRecolor() {
        var team = Team.create(organization, "Design", "#23395D");

        team.recolor("#2f7d6d");

        assertThat(team.color()).isEqualTo("#2F7D6D");
    }
}
