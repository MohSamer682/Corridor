package com.corridor.office.presence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.corridor.office.presence.domain.MovementIntent.Enter;
import com.corridor.office.presence.domain.MovementIntent.Leave;
import com.corridor.office.presence.domain.MovementIntent.Move;
import com.corridor.office.spaces.TileGrid;
import com.corridor.office.spaces.TileGrid.Tile;
import java.util.ArrayList;
import java.util.Arrays;
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

    private AvatarState where(UUID person) {
        return floor.avatar(person).orElseThrow().state();
    }

    private void move(UUID person, double x, double y) {
        floor.submit(new Move(person, x, y, 0, true));
    }

    private static TileGrid openFloor(int width, int height) {
        var open = "C" + ".".repeat(width - 2) + "C";
        return TileGrid.parse(
                width, height, "C".repeat(width) + open.repeat(height - 2) + "C".repeat(width), new Tile(1, 1));
    }

    @Test
    void shouldEnterOnTheFreeTileNearestTheArrival() {
        floor.submit(new Enter(nour));
        floor.submit(new Enter(omar));

        var frame = floor.tick();

        assertThat(where(nour).x()).isEqualTo(1.5);
        assertThat(where(nour).y()).isEqualTo(1.5);
        assertThat(where(omar).x()).isEqualTo(2.5);
        assertThat(frame.moved()).extracting(AvatarState::userId).containsExactly(nour, omar);
    }

    @Test
    void shouldAcceptSmallMovesInAnyDirection() {
        floor.submit(new Enter(nour));
        floor.tick();

        move(nour, 1.85, 1.65);
        var frame = floor.tick();

        assertThat(where(nour).x()).isEqualTo(1.85);
        assertThat(where(nour).y()).isEqualTo(1.65);
        assertThat(where(nour).moving()).isTrue();
        assertThat(frame.moved()).extracting(AvatarState::userId).containsExactly(nour);
        assertThat(frame.corrected()).isEmpty();
    }

    @Test
    void shouldPullBackAMoveIntoAWall() {
        floor.submit(new Enter(nour));
        floor.tick();

        move(nour, 1.5, 2.2);
        var frame = floor.tick();

        assertThat(where(nour).y()).isEqualTo(1.5);
        assertThat(frame.corrected()).extracting(AvatarState::userId).containsExactly(nour);
    }

    @Test
    void shouldPullBackATeleport() {
        floor.submit(new Enter(nour));
        floor.tick();

        move(nour, 5.5, 3.5);
        var frame = floor.tick();

        assertThat(where(nour).x()).isEqualTo(1.5);
        assertThat(frame.corrected()).hasSize(1);
    }

    @Test
    void shouldAllowLongerMovesAfterQuietTicks() {
        floor.submit(new Enter(nour));
        floor.tick();
        ticks(5);

        move(nour, 4.5, 1.5);
        floor.tick();

        assertThat(where(nour).x()).isEqualTo(4.5);
    }

    @Test
    void shouldNeverLetTwoPeopleOverlap() {
        floor.submit(new Enter(nour));
        floor.submit(new Enter(omar));
        floor.tick();

        move(omar, 1.9, 1.5);
        var frame = floor.tick();

        assertThat(where(omar).x()).isEqualTo(2.5);
        assertThat(frame.corrected()).extracting(AvatarState::userId).containsExactly(omar);
    }

    @Test
    void shouldLetSomeoneTooCloseStepAway() {
        floor.submit(new Enter(nour));
        floor.submit(new Enter(omar));
        floor.tick();

        move(omar, 2.7, 1.5);
        floor.tick();

        assertThat(where(omar).x()).isEqualTo(2.7);
    }

    @Test
    void shouldReportSettlingOnceAfterStandingStill() {
        floor.submit(new Enter(nour));
        floor.tick();
        floor.submit(new Move(nour, 2.4, 1.6, 0, false));

        var settled = new ArrayList<Settled>();
        for (int i = 0; i < FloorSession.SETTLE_TICKS + 5; i++)
            settled.addAll(floor.tick().settled());

        assertThat(settled).containsExactly(new Settled(nour, new Tile(2, 1)));
    }

    @Test
    void shouldReportOnlyAvatarsThatChanged() {
        floor.submit(new Enter(nour));
        floor.submit(new Enter(omar));
        floor.tick();

        move(nour, 1.6, 1.5);
        var frame = floor.tick();

        assertThat(frame.moved()).extracting(AvatarState::userId).containsExactly(nour);
        assertThat(floor.tick().moved()).isEmpty();
    }

    @Test
    void shouldForgetSomeoneWhoLeaves() {
        floor.submit(new Enter(nour));
        floor.tick();

        floor.submit(new Leave(nour));
        var frame = floor.tick();

        assertThat(frame.left()).containsExactly(nour);
        assertThat(floor.isEmpty()).isTrue();
    }

    @Test
    void shouldKeepEveryoneApartWhileThirtyPeopleWander() {
        var crowd = new FloorSession(openFloor(20, 12));
        var random = new Random(7);
        var people = new ArrayList<UUID>();
        for (int i = 0; i < 30; i++) {
            people.add(UUID.randomUUID());
            crowd.submit(new Enter(people.getLast()));
        }
        crowd.tick();
        for (int tick = 0; tick < 2_000; tick++) {
            for (var person : people) {
                var at = crowd.avatar(person).orElseThrow().state();
                var angle = random.nextDouble() * Math.PI * 2;
                crowd.submit(
                        new Move(person, at.x() + Math.cos(angle) * 0.4, at.y() + Math.sin(angle) * 0.4, angle, true));
            }
            crowd.tick();
            var everyone = crowd.everyone();
            for (int i = 0; i < everyone.size(); i++) {
                for (int j = i + 1; j < everyone.size(); j++) {
                    var a = everyone.get(i);
                    var b = everyone.get(j);
                    assertThat(Math.hypot(a.x() - b.x(), a.y() - b.y()))
                            .isGreaterThanOrEqualTo(FloorSession.MIN_GAP - 1e-9);
                }
            }
        }
    }

    @Test
    void shouldTickTwoHundredPeopleWellInsideTheTickBudget() {
        var crowd = new FloorSession(openFloor(60, 40));
        var random = new Random(11);
        var people = new ArrayList<UUID>();
        for (int i = 0; i < 200; i++) {
            people.add(UUID.randomUUID());
            crowd.submit(new Enter(people.getLast()));
        }
        crowd.tick();
        var times = new long[600];
        for (int tick = 0; tick < times.length; tick++) {
            for (var person : people) {
                var at = crowd.avatar(person).orElseThrow().state();
                var angle = random.nextDouble() * Math.PI * 2;
                crowd.submit(
                        new Move(person, at.x() + Math.cos(angle) * 0.5, at.y() + Math.sin(angle) * 0.5, angle, true));
            }
            var start = System.nanoTime();
            crowd.tick();
            times[tick] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        var p99 = times[times.length * 99 / 100] / 1_000_000.0;
        System.out.printf("200 people all moving: p99 tick %.2f ms%n", p99);
        // The tick runs every 100 ms; under a third of that leaves room for sending frames on a slow CI box.
        assertThat(p99).isLessThan(30.0);
    }
}
