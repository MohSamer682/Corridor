package com.corridor.office.conversations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationGroupsTest {

    private final UUID nour = UUID.randomUUID();
    private final UUID omar = UUID.randomUUID();
    private final UUID nadia = UUID.randomUUID();
    private final UUID office = UUID.randomUUID();

    private static Spot open(UUID who, int x, int y) {
        return new Spot(who, x, y, null);
    }

    @Test
    void shouldGroupPeopleWithinThreeTilesOnTheOpenFloor() {
        var groups = ConversationGroups.of(List.of(open(nour, 5, 5), open(omar, 8, 7)), Set.of());

        assertThat(groups).singleElement().satisfies(group -> assertThat(group.members())
                .containsExactlyInAnyOrder(nour, omar));
    }

    @Test
    void shouldNotGroupPeopleFourTilesApart() {
        var groups = ConversationGroups.of(List.of(open(nour, 5, 5), open(omar, 9, 5)), Set.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void shouldChainPeopleWhoEachHearSomeoneInTheGroup() {
        var groups = ConversationGroups.of(List.of(open(nour, 0, 0), open(omar, 3, 0), open(nadia, 6, 0)), Set.of());

        assertThat(groups).singleElement().satisfies(group -> assertThat(group.members())
                .hasSize(3));
    }

    @Test
    void shouldLeaveSomeoneInFocusOutOfEveryGroup() {
        var groups =
                ConversationGroups.of(List.of(open(nour, 5, 5), open(omar, 6, 5), open(nadia, 5, 6)), Set.of(nadia));

        assertThat(groups).singleElement().satisfies(group -> assertThat(group.members())
                .containsExactlyInAnyOrder(nour, omar));
    }

    @Test
    void shouldKeepPeopleOutsideAPrivateRoomOut() {
        var inside = new Spot(nour, 5, 5, office);
        var alsoInside = new Spot(omar, 7, 5, office);
        var outsideTheGlass = open(nadia, 5, 6);

        var groups = ConversationGroups.of(List.of(inside, alsoInside, outsideTheGlass), Set.of());

        assertThat(groups).singleElement().satisfies(group -> assertThat(group.members())
                .containsExactlyInAnyOrder(nour, omar));
    }

    @Test
    void shouldGroupEveryoneInAPrivateRoomHoweverFarApart() {
        var groups =
                ConversationGroups.of(List.of(new Spot(nour, 1, 1, office), new Spot(omar, 9, 9, office)), Set.of());

        assertThat(groups).hasSize(1);
    }

    @Test
    void shouldGiveTheSameGroupTheSameIdWhateverTheOrder() {
        var first = ConversationGroups.of(List.of(open(nour, 5, 5), open(omar, 6, 5)), Set.of());
        var second = ConversationGroups.of(List.of(open(omar, 6, 5), open(nour, 5, 5)), Set.of());

        assertThat(first.getFirst().id()).isEqualTo(second.getFirst().id());
    }
}
