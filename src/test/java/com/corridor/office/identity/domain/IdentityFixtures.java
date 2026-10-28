package com.corridor.office.identity.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

public final class IdentityFixtures {

    private IdentityFixtures() {}

    public static Organization organization() {
        return organization("corridor");
    }

    public static Organization organization(String slug) {
        return Organization.create(
                "Corridor Technologies",
                slug,
                ZoneId.of("Africa/Cairo"),
                Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
                LocalTime.of(9, 0));
    }
}
