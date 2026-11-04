package com.corridor.office.attendance.application;

import com.corridor.office.attendance.domain.Span;
import com.corridor.office.attendance.domain.WorkDay;
import com.corridor.office.attendance.domain.WorkWeekRules;
import com.corridor.office.attendance.infrastructure.PunchStore;
import com.corridor.office.attendance.infrastructure.PunchStore.PersonSpan;
import com.corridor.office.common.api.PageResponse;
import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.identity.UserDirectory.Member;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Work days, worked out from punches when they are read. You see your own; managers see their own team; HR and
 * admins see everyone in the company.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceQuery {

    public static final int PAGE_SIZE = 20;
    private static final int MAX_DAYS = 62;
    private static final WorkWeekRules FALLBACK = new WorkWeekRules(
            ZoneId.of("Africa/Cairo"), LocalTime.of(9, 0), Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));

    /** One person's day in the team view. */
    public record TeamDay(UUID userId, String displayName, String teamName, WorkDay day) {}

    private final PunchStore punches;
    private final UserDirectory directory;
    private final Clock clock;

    AttendanceQuery(PunchStore punches, UserDirectory directory, Clock clock) {
        this.punches = punches;
        this.directory = directory;
        this.clock = clock;
    }

    public List<WorkDay> mine(CurrentUser viewer, LocalDate from, LocalDate to) {
        return days(viewer.organizationId(), viewer.userId(), from, to);
    }

    public List<WorkDay> person(CurrentUser viewer, UUID userId, LocalDate from, LocalDate to) {
        var allowed = viewer.userId().equals(userId)
                || seesEveryone(viewer)
                || (viewer.role() == Role.MANAGER && sameTeam(viewer, userId));
        if (!allowed) {
            throw new ForbiddenException("attendance.notYourTeam");
        }
        return days(viewer.organizationId(), userId, from, to);
    }

    public PageResponse<TeamDay> team(CurrentUser viewer, LocalDate day, UUID teamId, String query, int page) {
        var people = visiblePeople(viewer, teamId).stream()
                .filter(m -> query == null
                        || m.displayName()
                                .toLowerCase(Locale.ROOT)
                                .contains(query.strip().toLowerCase(Locale.ROOT)))
                .toList();
        // shortcut: pages a company's people in memory; fine for hundreds, page in SQL past a few thousand.
        var slice =
                people.stream().skip((long) page * PAGE_SIZE).limit(PAGE_SIZE).toList();
        var week = week(viewer.organizationId());
        var spans = spansByPerson(
                viewer.organizationId(), slice.stream().map(Member::userId).toList(), day, day, week);
        var items = slice.stream()
                .map(m -> new TeamDay(
                        m.userId(),
                        m.displayName(),
                        m.teamName(),
                        WorkDay.of(day, spans.getOrDefault(m.userId(), List.of()), week, clock.instant())))
                .toList();
        var pages = (int) Math.ceil(people.size() / (double) PAGE_SIZE);
        return new PageResponse<>(items, page, PAGE_SIZE, people.size(), pages);
    }

    private List<Member> visiblePeople(CurrentUser viewer, UUID teamId) {
        if (seesEveryone(viewer)) {
            return directory.members(viewer.organizationId(), teamId);
        }
        if (viewer.role() != Role.MANAGER) {
            throw new ForbiddenException("attendance.notYourTeam");
        }
        var own = teamOf(viewer);
        if (own == null || (teamId != null && !teamId.equals(own))) {
            throw new ForbiddenException("attendance.notYourTeam");
        }
        return directory.members(viewer.organizationId(), own);
    }

    private List<WorkDay> days(UUID organizationId, UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new InvalidInputException("attendance.badRange");
        }
        var week = week(organizationId);
        var spans =
                spansByPerson(organizationId, List.of(userId), from, to, week).getOrDefault(userId, List.of());
        var now = clock.instant();
        return from.datesUntil(to.plusDays(1))
                .sorted(Comparator.reverseOrder())
                .map(day -> WorkDay.of(
                        day,
                        spans.stream()
                                .filter(s -> s.from()
                                        .atZone(week.zone())
                                        .toLocalDate()
                                        .equals(day))
                                .toList(),
                        week,
                        now))
                .toList();
    }

    private Map<UUID, List<Span>> spansByPerson(
            UUID organizationId, Collection<UUID> people, LocalDate from, LocalDate to, WorkWeekRules week) {
        if (people.isEmpty()) {
            return Map.of();
        }
        var start = from.atStartOfDay(week.zone()).toInstant();
        var end = to.plusDays(1).atStartOfDay(week.zone()).toInstant();
        return punches.spans(organizationId, people, start, end).stream()
                .collect(Collectors.groupingBy(
                        PersonSpan::userId, Collectors.mapping(PersonSpan::span, Collectors.toList())));
    }

    private WorkWeekRules week(UUID organizationId) {
        return directory
                .workWeek(organizationId)
                .map(w -> new WorkWeekRules(w.zone(), w.startsAt(), w.weekend()))
                .orElse(FALLBACK);
    }

    private static boolean seesEveryone(CurrentUser viewer) {
        return viewer.role() == Role.HR || viewer.role() == Role.ADMIN;
    }

    private UUID teamOf(CurrentUser viewer) {
        return directory.members(viewer.organizationId(), null).stream()
                .filter(m -> m.userId().equals(viewer.userId()))
                .map(Member::teamId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean sameTeam(CurrentUser viewer, UUID userId) {
        var own = teamOf(viewer);
        return own != null
                && directory.members(viewer.organizationId(), own).stream()
                        .anyMatch(m -> m.userId().equals(userId));
    }
}
