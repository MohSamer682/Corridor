package com.corridor.office.presence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.presence.domain.MovementIntent.Enter;
import com.corridor.office.presence.domain.MovementIntent.Leave;
import com.corridor.office.presence.domain.MovementIntent.Step;
import com.corridor.office.presence.domain.MovementIntent.WalkTo;
import com.corridor.office.spaces.TileGrid;
import com.corridor.office.spaces.TileGrid.Tile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorSessionTest {

    // 7 x 5: a wall across the middle row with one gap at (3,2).
    private static final String ROOMS = "CCCCCCC" + "C.....C" + "C##.##C" + "C.....C" + "CCCCCCC";
    private static final Tile ARRIVAL = new Tile(1, 1);

    private final UUID nour = UUID.randomUUID();
    private final UUID omar = UUID.randomUUID();
    private final FloorSession floor = new FloorSession(TileGrid.parse(7, 5, ROOMS, ARRIVAL));

    private Frame ticks(int count) {
        Frame last = Frame.EMPTY;
        for (int i = 0; i < count; i++) last = floor.tick();
        return last;
    }

    private Tile tileOf(UUID person) {
        return floor.avatar(person).orElseThrow().tile();
    }

    @Test
    void shouldEnterOnTheFreeTileNearestTheArrival() {
        floor.submit(new Enter(nour));
        floor.submit(new Enter(omar));

        var frame = floor.tick();

        assertThat(tileOf(nour)).isEqualTo(ARRIVAL);
        assertThat(tileOf(omar)).isEqualTo(new Tile(2, 1));
        assertThat(frame.moved()).extracting(AvatarState::userId).containsExactly(nour, omar);
    }

    @Test
    void shouldWalkThroughTheGapAroundTheWall() {
        floor.submit(new Enter(nour));
        floor.tick();

        floor.submit(new WalkTo(nour, new Tile(1, 3)));
        ticks(40);

        assertThat(tileOf(nour)).isEqualTo(new Tile(1, 3));
    }

    @Test
    void shouldTakeOneTileEveryTwoTicks() {
        floor.submit(new Enter(nour));
        floor.tick();
        floor.submit(new WalkTo(nour, new Tile(3, 1)));

        var first = floor.tick();
        assertThat(first.moved()).singleElement().satisfies(state -> {
            assertThat(state.x()).isEqualTo(2);
            assertThat(state.moving()).isTrue();
            assertThat(state.facing()).isEqualTo('E');
        });
        floor.tick();
        assertThat(tileOf(nour)).isEqualTo(new Tile(1, 1));
        floor.tick();
        assertThat(tileOf(nour)).isEqualTo(new Tile(2, 1));
        ticks(2);
        assertThat(tileOf(nour)).isEqualTo(new Tile(3, 1));
    }

    @Test
    void shouldRejectAWalkIntoAWall() {
        floor.submit(new Enter(nour));
        floor.tick();

        assertThatThrownBy(() -> floor.submit(new WalkTo(nour, new Tile(1, 2))))
                .isInstanceOf(InvalidMoveException.class);
        assertThatThrownBy(() -> floor.submit(new WalkTo(nour, new Tile(40, 40))))
                .isInstanceOf(InvalidMoveException.class);
    }

    @Test
    void shouldNeverLetTwoPeopleHoldTheSameTile() {
        floor.submit(new Enter(nour));
        floor.submit(new Enter(omar));
        floor.tick();
        floor.submit(new WalkTo(omar, new Tile(3, 3)));
        floor.submit(new WalkTo(nour, new Tile(3, 3)));

        for (int i = 0; i < 60; i++) {
            floor.tick();
            assertThat(floor.heldTiles()).doesNotHaveDuplicates();
        }
        assertThat(List.of(tileOf(nour), tileOf(omar))).contains(new Tile(3, 3));
    }

    @Test
    void shouldGiveUpWhenSomeoneStaysOnTheTarget() {
        floor.submit(new Enter(nour));
        floor.submit(new Enter(omar));
        floor.tick();
        floor.submit(new WalkTo(omar, new Tile(3, 2)));
        ticks(10);
        assertThat(tileOf(omar)).isEqualTo(new Tile(3, 2));

        floor.submit(new WalkTo(nour, new Tile(3, 2)));
        ticks(FloorSession.GIVE_UP_TICKS + 10);

        assertThat(floor.avatar(nour).orElseThrow().walking()).isFalse();
        assertThat(tileOf(nour)).isNotEqualTo(new Tile(3, 2));
    }

    @Test
    void shouldStepOneTileWithTheKeyboardButNotIntoWalls() {
        floor.submit(new Enter(nour));
        floor.tick();

        floor.submit(new Step(nour, 1, 0));
        ticks(3);
        floor.submit(new Step(nour, 0, 1));
        ticks(3);

        assertThat(tileOf(nour)).isEqualTo(new Tile(2, 1));
    }

    @Test
    void shouldApplyIntentsInArrivalOrderOnTheTick() {
        floor.submit(new Enter(nour));
        floor.submit(new Leave(nour));

        var frame = floor.tick();

        assertThat(floor.avatar(nour)).isEmpty();
        assertThat(frame.left()).containsExactly(nour);
        assertThat(floor.isEmpty()).isTrue();
    }

    @Test
    void shouldReportOnlyAvatarsThatMoved() {
        floor.submit(new Enter(nour));
        floor.submit(new Enter(omar));
        floor.tick();

        floor.submit(new WalkTo(omar, new Tile(5, 1)));
        var frame = floor.tick();

        assertThat(frame.moved()).extracting(AvatarState::userId).containsExactly(omar);
        assertThat(ticks(60).moved()).isEmpty();
    }

    @Test
    void shouldReportSettlingOnceAfterStandingStill() {
        floor.submit(new Enter(nour));
        floor.tick();
        floor.submit(new WalkTo(nour, new Tile(5, 1)));

        var settled = new ArrayList<Settled>();
        for (int i = 0; i < FloorSession.SETTLE_TICKS + 40; i++)
            settled.addAll(floor.tick().settled());

        assertThat(settled).containsExactly(new Settled(nour, new Tile(5, 1)));
    }

    @Test
    void shouldKeepEveryoneOnTheirOwnTileWhileThirtyPeopleWander() {
        var open = "C" + ".".repeat(18) + "C";
        var grid = TileGrid.parse(20, 12, "C".repeat(20) + open.repeat(10) + "C".repeat(20), new Tile(1, 1));
        var crowd = new FloorSession(grid);
        var random = new Random(7);
        var people = new ArrayList<UUID>();
        for (int i = 0; i < 30; i++) {
            var person = UUID.randomUUID();
            people.add(person);
            crowd.submit(new Enter(person));
        }
        for (int tick = 0; tick < 2_000; tick++) {
            if (tick % 5 == 0) {
                var person = people.get(random.nextInt(people.size()));
                crowd.submit(new WalkTo(person, new Tile(1 + random.nextInt(18), 1 + random.nextInt(10))));
            }
            crowd.tick();
            var held = crowd.heldTiles();
            assertThat(new HashSet<>(held)).hasSameSizeAs(held);
        }
    }

    @Test
    void shouldTickTwoHundredPeopleWellInsideTheTickBudget() {
        var open = "C" + ".".repeat(58) + "C";
        var grid = TileGrid.parse(60, 40, "C".repeat(60) + open.repeat(38) + "C".repeat(60), new Tile(1, 1));
        var crowd = new FloorSession(grid);
        var random = new Random(11);
        var people = new ArrayList<UUID>();
        for (int i = 0; i < 200; i++) {
            people.add(UUID.randomUUID());
            crowd.submit(new Enter(people.getLast()));
        }
        var times = new long[600];
        var mostMoved = 0;
        for (int tick = 0; tick < times.length; tick++) {
            // Everyone picks a new spot about every three seconds.
            for (var person : people) {
                if (random.nextInt(30) == 0) {
                    crowd.submit(new WalkTo(person, new Tile(1 + random.nextInt(58), 1 + random.nextInt(38))));
                }
            }
            var start = System.nanoTime();
            var frame = crowd.tick();
            times[tick] = System.nanoTime() - start;
            mostMoved = Math.max(mostMoved, frame.moved().size());
        }
        java.util.Arrays.sort(times);
        var p99 = times[times.length * 99 / 100] / 1_000_000.0;
        System.out.printf(
                "200 people: p99 tick %.2f ms, max %.2f ms, most moved in one frame %d%n",
                p99, times[times.length - 1] / 1_000_000.0, mostMoved);
        // The tick runs every 100 ms; under a third of that leaves room for sending frames on a slow CI box.
        assertThat(p99).isLessThan(30.0);
    }
}
