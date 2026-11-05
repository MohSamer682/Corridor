package com.corridor.office.insights.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What leaders see at a glance. {@code scope} is "Everyone" for HR and admins and the team's name for a manager;
 * people counts follow the scope, floors and rooms are always the whole company.
 */
public record CommandCenter(
        Instant at,
        String scope,
        Kpis kpis,
        List<FloorCount> floors,
        List<RoomUse> rooms,
        List<TeamRow> teams,
        List<String> attention,
        List<DayCount> days) {

    public record Kpis(
            int people, int online, int inMeetings, int inFocus, int punchedIn, int late, int roomsInUse, int rooms) {}

    public record FloorCount(UUID floorId, int online) {}

    /** A meeting or conference room; {@code meeting} is null while it's free. */
    public record RoomUse(UUID roomId, String name, String place, String meeting, int people, int capacity) {}

    public record TeamRow(String team, int people, int online, int punchedIn, int late, int inMeetings) {}

    /** How many people punched in on a day, in the company's time zone. */
    public record DayCount(LocalDate day, int present, boolean weekend) {}
}
