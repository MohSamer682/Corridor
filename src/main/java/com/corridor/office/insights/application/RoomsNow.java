package com.corridor.office.insights.application;

import com.corridor.office.insights.application.CommandCenter.RoomUse;
import com.corridor.office.spaces.SpaceCatalog;
import com.corridor.office.spaces.SpaceCatalog.BookableRoom;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The third query: every meeting and conference room, its seats, and the meeting in it right now. */
@Component
class RoomsNow {

    /** The rooms, and everyone invited to a meeting that is on right now. */
    record Rooms(List<RoomUse> rooms, Set<UUID> inMeetings) {}

    private record Now(int capacity, String meeting, Set<UUID> people) {}

    private final JdbcClient jdbc;
    private final SpaceCatalog spaces;

    RoomsNow(JdbcClient jdbc, SpaceCatalog spaces) {
        this.jdbc = jdbc;
        this.spaces = spaces;
    }

    Rooms at(UUID organizationId, Instant now) {
        var rooms = spaces.bookableRooms(organizationId);
        var byRoom = new HashMap<UUID, Now>();
        jdbc.sql(
                        """
                        SELECT r.id, (SELECT count(*) FROM seats s WHERE s.room_id = r.id) AS capacity,
                               m.title, m.organizer_id,
                               ARRAY(SELECT a.user_id FROM meeting_attendees a WHERE a.meeting_id = m.id) AS invited
                        FROM rooms r
                        LEFT JOIN meetings m ON m.room_id = r.id AND m.status IN ('SCHEDULED', 'LIVE')
                             AND m.starts_at <= :now AND m.ends_at > :now
                        WHERE r.id = ANY(:rooms)""")
                .param("now", Timestamp.from(now))
                .param("rooms", rooms.stream().map(BookableRoom::id).toArray(UUID[]::new))
                .query(rs -> {
                    var people = new HashSet<UUID>(uuids(rs.getArray("invited")));
                    var organizer = rs.getObject("organizer_id", UUID.class);
                    if (organizer != null) {
                        people.add(organizer);
                    }
                    byRoom.put(
                            rs.getObject("id", UUID.class),
                            new Now(rs.getInt("capacity"), rs.getString("title"), people));
                });
        return rooms(rooms, byRoom);
    }

    private static Rooms rooms(List<BookableRoom> rooms, HashMap<UUID, Now> byRoom) {
        var inMeetings = new HashSet<UUID>();
        var uses = rooms.stream()
                .map(room -> {
                    var now = byRoom.getOrDefault(room.id(), new Now(0, null, Set.of()));
                    inMeetings.addAll(now.people());
                    return new RoomUse(
                            room.id(),
                            room.name(),
                            room.place(),
                            now.meeting(),
                            now.people().size(),
                            now.capacity());
                })
                .toList();
        return new Rooms(uses, inMeetings);
    }

    private static List<UUID> uuids(Array array) throws SQLException {
        return array == null ? List.of() : List.of((UUID[]) array.getArray());
    }
}
