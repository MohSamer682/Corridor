package com.corridor.office.announcements.application;

import com.corridor.office.announcements.events.TownHallStarted;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.spaces.SpaceCatalog;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One live town hall per company, kept in memory like presence (ADR-006); a second call is refused. */
@Service
public class TownHallService {

    /** The live town hall and where to walk to join it. */
    public record TownHall(
            String title, UUID floorId, double x, double y, String roomName, UUID calledBy, Instant startedAt) {}

    private final Map<UUID, TownHall> live = new ConcurrentHashMap<>();
    private final SpaceCatalog spaces;
    private final UserDirectory directory;
    private final SimpMessagingTemplate sockets;
    private final AnnouncementEvents events;

    TownHallService(
            SpaceCatalog spaces, UserDirectory directory, SimpMessagingTemplate sockets, AnnouncementEvents events) {
        this.spaces = spaces;
        this.directory = directory;
        this.sockets = sockets;
        this.events = events;
    }

    @Transactional
    public TownHall start(CurrentUser caller, String title) {
        Broadcasters.check(caller);
        var name = title == null || title.isBlank() ? "Town hall" : title.strip();
        var hall = spaces.townHall(caller.organizationId())
                .orElseThrow(() -> new NotFoundException("announcements.noHall"));
        var townHall = new TownHall(
                name,
                hall.floorId(),
                hall.x(),
                hall.y(),
                hall.name(),
                caller.userId(),
                events.clock().instant());
        if (live.putIfAbsent(caller.organizationId(), townHall) != null) {
            throw new ConflictException("announcements.townHallLive");
        }
        events.publisher().publishEvent(new TownHallStarted(caller.organizationId(), caller.userId(), name));
        for (var member : directory.members(caller.organizationId(), null)) {
            sockets.convertAndSendToUser(member.userId().toString(), "/queue/town-hall", townHall);
        }
        return townHall;
    }

    public Optional<TownHall> current(CurrentUser viewer) {
        return Optional.ofNullable(live.get(viewer.organizationId()));
    }

    public void end(CurrentUser caller) {
        Broadcasters.check(caller);
        live.remove(caller.organizationId());
    }
}
