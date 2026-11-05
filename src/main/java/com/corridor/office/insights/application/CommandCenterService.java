package com.corridor.office.insights.application;

import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.identity.UserDirectory.WorkWeek;
import com.corridor.office.insights.application.CommandCenter.DayCount;
import com.corridor.office.insights.application.CommandCenter.FloorCount;
import com.corridor.office.insights.application.CommandCenter.Kpis;
import com.corridor.office.insights.application.CommandCenter.RoomUse;
import com.corridor.office.insights.application.CommandCenter.TeamRow;
import com.corridor.office.insights.application.PeopleToday.Person;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the Command Center from three queries (people with today's first punch, rooms with the meeting in them now,
 * 14 days of head counts) plus who is on a floor and in focus, which only presence keeps in memory.
 */
@Service
public class CommandCenterService {

    private static final WorkWeek FALLBACK =
            new WorkWeek(ZoneId.of("Africa/Cairo"), LocalTime.of(9, 0), Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
    // Someone is flagged as not clocked in once work has been going for this long.
    private static final Duration GRACE = Duration.ofMinutes(15);
    private static final int MAX_NAMED = 5;
    private static final int DAYS = 14;

    private final PeopleToday people;
    private final RoomsNow rooms;
    private final LiveSignals live;
    private final UserDirectory directory;
    private final Clock clock;

    CommandCenterService(PeopleToday people, RoomsNow rooms, LiveSignals live, UserDirectory directory, Clock clock) {
        this.people = people;
        this.rooms = rooms;
        this.live = live;
        this.directory = directory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CommandCenter snapshot(CurrentUser viewer) {
        return snapshot(viewer, clock.instant());
    }

    // shortcut: built fresh on every call (3 queries, well under 200 ms for 1,000 people); cache per company if it
    // isn't.
    @Transactional(readOnly = true)
    CommandCenter snapshot(CurrentUser viewer, Instant now) {
        if (viewer.role() == Role.EMPLOYEE) {
            throw new ForbiddenException("insights.notAllowed");
        }
        var week = directory.workWeek(viewer.organizationId()).orElse(FALLBACK);
        var today = now.atZone(week.zone()).toLocalDate();
        var dayStart = today.atStartOfDay(week.zone()).toInstant();
        var everyone = people.people(viewer.organizationId(), dayStart, dayStart.plus(Duration.ofDays(1)));
        var scope = scope(viewer, everyone);
        var mine = everyone.stream().filter(scope.members()).toList();
        var onFloors = live.online().floorsOf(viewer.organizationId());
        var roomsNow = rooms.at(viewer.organizationId(), now);
        var day =
                new Day(today, today.atTime(week.startsAt()).atZone(week.zone()).toInstant(), week, now);
        var counts = new Counts(onFloors.keySet(), roomsNow.inMeetings(), live, day);
        return new CommandCenter(
                now,
                scope.name(),
                kpis(mine, counts, roomsNow.rooms()),
                floors(onFloors),
                roomsNow.rooms(),
                teams(mine, counts),
                attention(mine, roomsNow.rooms(), day),
                days(viewer, mine, day));
    }

    private record Scope(String name, Predicate<Person> members) {}

    /** HR and admins see everyone; a manager sees their own team, or only themselves without one. */
    private static Scope scope(CurrentUser viewer, List<Person> everyone) {
        if (viewer.role() != Role.MANAGER) {
            return new Scope("Everyone", person -> true);
        }
        var me = everyone.stream()
                .filter(p -> p.id().equals(viewer.userId()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("insights.notAllowed"));
        return me.teamId() == null
                ? new Scope("Only you", p -> p.id().equals(me.id()))
                : new Scope(me.team(), p -> me.teamId().equals(p.teamId()));
    }

    private record Day(LocalDate date, Instant workStarts, WorkWeek week, Instant now) {

        boolean workday() {
            return !week.weekend().contains(date.getDayOfWeek());
        }
    }

    private record Counts(Set<UUID> online, Set<UUID> inMeetings, LiveSignals live, Day day) {

        boolean late(Person p) {
            return day.workday() && p.firstIn() != null && p.firstIn().isAfter(day.workStarts());
        }

        int count(List<Person> people, Predicate<Person> test) {
            return (int) people.stream().filter(test).count();
        }
    }

    private static Kpis kpis(List<Person> mine, Counts c, List<RoomUse> rooms) {
        return new Kpis(
                mine.size(),
                c.count(mine, p -> c.online().contains(p.id())),
                c.count(mine, p -> c.inMeetings().contains(p.id())),
                c.count(
                        mine,
                        p -> c.online().contains(p.id()) && c.live().focus().inFocus(p.id())),
                c.count(mine, Person::in),
                c.count(mine, c::late),
                (int) rooms.stream().filter(room -> room.meeting() != null).count(),
                rooms.size());
    }

    private static List<FloorCount> floors(Map<UUID, UUID> onFloors) {
        return onFloors.values().stream()
                .collect(Collectors.groupingBy(floor -> floor, Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> new FloorCount(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(FloorCount::online).reversed())
                .toList();
    }

    private static List<TeamRow> teams(List<Person> mine, Counts c) {
        var byTeam = mine.stream()
                .collect(Collectors.groupingBy(
                        p -> Objects.requireNonNullElse(p.team(), "No team"), TreeMap::new, Collectors.toList()));
        return byTeam.entrySet().stream()
                .map(e -> new TeamRow(
                        e.getKey(),
                        e.getValue().size(),
                        c.count(e.getValue(), p -> c.online().contains(p.id())),
                        c.count(e.getValue(), Person::in),
                        c.count(e.getValue(), c::late),
                        c.count(e.getValue(), p -> c.inMeetings().contains(p.id()))))
                .toList();
    }

    /** Who hasn't clocked in once work has been going a while, and rooms that are nearly full. */
    private static List<String> attention(List<Person> mine, List<RoomUse> rooms, Day day) {
        var lines = new ArrayList<String>();
        if (day.workday() && day.now().isAfter(day.workStarts().plus(GRACE))) {
            var missing =
                    mine.stream().filter(p -> p.firstIn() == null && !p.in()).toList();
            missing.stream().limit(MAX_NAMED).forEach(p -> lines.add(firstName(p.name()) + " has not clocked in."));
            if (missing.size() > MAX_NAMED) {
                lines.add((missing.size() - MAX_NAMED) + " more people have not clocked in.");
            }
        }
        rooms.stream()
                .filter(room -> room.meeting() != null && room.capacity() > 0)
                .filter(room -> room.people() * 10 >= room.capacity() * 8)
                .forEach(room -> lines.add(
                        "%s is at %d%% capacity.".formatted(room.name(), room.people() * 100 / room.capacity())));
        return lines;
    }

    private List<DayCount> days(CurrentUser viewer, List<Person> mine, Day day) {
        var from = day.date().minusDays(DAYS - 1);
        var present = people.present(
                viewer.organizationId(),
                mine.stream().map(Person::id).toList(),
                from.atStartOfDay(day.week().zone()).toInstant(),
                day.week().zone());
        return from.datesUntil(day.date().plusDays(1))
                .map(d -> new DayCount(
                        d, present.getOrDefault(d, 0), day.week().weekend().contains(d.getDayOfWeek())))
                .toList();
    }

    private static String firstName(String displayName) {
        var space = displayName.indexOf(' ');
        return space < 0 ? displayName : displayName.substring(0, space);
    }
}
