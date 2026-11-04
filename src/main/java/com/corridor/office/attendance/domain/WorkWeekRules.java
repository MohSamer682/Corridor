package com.corridor.office.attendance.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/** A company's working week: its time zone, when work starts, and which days are the weekend. */
public record WorkWeekRules(ZoneId zone, LocalTime startsAt, Set<DayOfWeek> weekend) {}
