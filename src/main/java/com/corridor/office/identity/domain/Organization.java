package com.corridor.office.identity.domain;

import com.corridor.office.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "organizations")
public class Organization extends AuditedEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 60)
    private String slug;

    @Column(nullable = false, length = 40)
    private ZoneId timeZone;

    @Column(nullable = false)
    private short[] weekendDays;

    @Column(nullable = false)
    private LocalTime workStartsAt;

    protected Organization() {}

    private Organization(
            String name, String slug, ZoneId timeZone, Set<DayOfWeek> weekendDays, LocalTime workStartsAt) {
        this.name = name;
        this.slug = slug;
        this.timeZone = timeZone;
        this.weekendDays = toIsoNumbers(weekendDays);
        this.workStartsAt = workStartsAt;
    }

    public static Organization create(
            String name, String slug, ZoneId timeZone, Set<DayOfWeek> weekendDays, LocalTime workStartsAt) {
        return new Organization(name, slug, timeZone, weekendDays, workStartsAt);
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }

    public ZoneId timeZone() {
        return timeZone;
    }

    public Set<DayOfWeek> weekendDays() {
        var days = EnumSet.noneOf(DayOfWeek.class);
        for (short day : weekendDays) {
            days.add(DayOfWeek.of(day));
        }
        return Collections.unmodifiableSet(days);
    }

    private static short[] toIsoNumbers(Set<DayOfWeek> days) {
        var numbers = new short[days.size()];
        var index = 0;
        for (DayOfWeek day : EnumSet.copyOf(days)) {
            numbers[index++] = (short) day.getValue();
        }
        return numbers;
    }

    public LocalTime workStartsAt() {
        return workStartsAt;
    }
}
