package com.corridor.office.presence.application;

import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.presence.domain.AvatarState;
import com.corridor.office.presence.domain.FloorSession;
import com.corridor.office.presence.domain.Frame;
import com.corridor.office.presence.domain.MovementIntent;
import com.corridor.office.presence.domain.MovementIntent.Enter;
import com.corridor.office.presence.domain.MovementIntent.Leave;
import com.corridor.office.presence.domain.MovementIntent.Step;
import com.corridor.office.presence.domain.MovementIntent.WalkTo;
import com.corridor.office.presence.events.FloorTicked;
import com.corridor.office.presence.events.FloorTicked.Standing;
import com.corridor.office.spaces.SpaceCatalog;
import com.corridor.office.spaces.TileGrid.Tile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Live presence, in memory (ADR-006). Intents from any socket thread go into the floor's queue; the
 * scheduler's single thread ticks every floor and sends one frame per floor that changed.
 */
@Service
public class PresenceService {

    private static final class LiveFloor {
        final UUID organizationId;
        final FloorSession session;
        volatile List<AvatarState> snapshot = List.of();

        LiveFloor(UUID organizationId, FloorSession session) {
            this.organizationId = organizationId;
            this.session = session;
        }
    }

    private final Map<UUID, LiveFloor> floors = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> floorOf = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> socketsOf = new ConcurrentHashMap<>();
    private final SpaceCatalog spaces;
    private final SimpMessagingTemplate messages;
    private final OpenSockets sockets;
    private final ApplicationEventPublisher events;

    PresenceService(
            SpaceCatalog spaces,
            SimpMessagingTemplate messages,
            OpenSockets sockets,
            ApplicationEventPublisher events) {
        this.spaces = spaces;
        this.messages = messages;
        this.sockets = sockets;
        this.events = events;
    }

    public void enter(CurrentUser person, UUID floorId) {
        var floor = floors.computeIfAbsent(floorId, id -> openFloor(person.organizationId(), id));
        if (!floor.organizationId.equals(person.organizationId())) {
            throw new NotFoundException("spaces.floorNotFound");
        }
        var previous = floorOf.put(person.userId(), floorId);
        if (previous != null && !previous.equals(floorId)) {
            submit(previous, new Leave(person.userId()));
        }
        floor.session.submit(new Enter(person.userId()));
    }

    public void walk(CurrentUser person, Tile target) {
        submitOnCurrentFloor(new WalkTo(person.userId(), target));
    }

    public void step(CurrentUser person, int dx, int dy) {
        submitOnCurrentFloor(new Step(person.userId(), Integer.signum(dx), Integer.signum(dy)));
    }

    public void leave(UUID userId) {
        var floorId = floorOf.remove(userId);
        if (floorId != null) {
            submit(floorId, new Leave(userId));
        }
    }

    public void connected(UUID userId, String socketId) {
        socketsOf.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(socketId);
    }

    /** The last closed socket of a person takes them off their floor. */
    public void disconnected(UUID userId, String socketId) {
        var remaining = socketsOf.computeIfPresent(userId, (id, open) -> {
            open.remove(socketId);
            return open.isEmpty() ? null : open;
        });
        if (remaining == null) {
            leave(userId);
        }
    }

    /** Takes a suspended or removed person out of the office and closes their sockets. */
    public void evict(UUID userId) {
        leave(userId);
        var open = socketsOf.remove(userId);
        if (open != null) {
            sockets.close(open);
        }
    }

    public List<AvatarState> occupants(CurrentUser viewer, UUID floorId) {
        if (!canWatch(viewer, floorId)) {
            throw new NotFoundException("spaces.floorNotFound");
        }
        var floor = floors.get(floorId);
        return floor == null ? List.of() : floor.snapshot;
    }

    /** How many people are on each floor of the viewer's company right now. */
    public Map<UUID, Integer> counts(CurrentUser viewer) {
        return floors.entrySet().stream()
                .filter(entry -> entry.getValue().organizationId.equals(viewer.organizationId()))
                .filter(entry -> !entry.getValue().snapshot.isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().snapshot.size()));
    }

    /** Whether the viewer may follow a floor: it exists and belongs to their company. */
    public boolean canWatch(CurrentUser viewer, UUID floorId) {
        var floor = floors.get(floorId);
        return floor != null
                ? floor.organizationId.equals(viewer.organizationId())
                : spaces.grid(viewer.organizationId(), floorId).isPresent();
    }

    @Scheduled(fixedRate = 100)
    void tick() {
        floors.forEach((floorId, floor) -> {
            Frame frame = floor.session.tick();
            if (!frame.isEmpty()) {
                floor.snapshot = floor.session.everyone();
                frame.settled().forEach(s -> spaces.sat(floor.organizationId, floorId, s.userId(), s.tile()));
                messages.convertAndSend("/topic/floors/" + floorId + "/frames", FrameMessage.of(frame));
                events.publishEvent(new FloorTicked(
                        floor.organizationId,
                        floorId,
                        floor.snapshot.stream()
                                .map(a -> new Standing(a.userId(), a.x(), a.y()))
                                .toList()));
            }
            // shortcut: an enter that races this removal is lost and the client re-enters on reconnect; fine on one
            // instance.
            if (floor.session.isEmpty()) {
                floors.remove(floorId, floor);
            }
        });
    }

    private LiveFloor openFloor(UUID organizationId, UUID floorId) {
        var grid =
                spaces.grid(organizationId, floorId).orElseThrow(() -> new NotFoundException("spaces.floorNotFound"));
        return new LiveFloor(organizationId, new FloorSession(grid));
    }

    private void submitOnCurrentFloor(MovementIntent intent) {
        var floorId = floorOf.get(intent.userId());
        if (floorId == null) {
            throw new NotFoundException("presence.notOnAFloor");
        }
        submit(floorId, intent);
    }

    private void submit(UUID floorId, MovementIntent intent) {
        var floor = floors.get(floorId);
        if (floor != null) {
            floor.session.submit(intent);
        }
    }

    /** What clients receive ten times a second: who moved and who left. */
    public record FrameMessage(List<AvatarState> moved, List<UUID> left) {

        static FrameMessage of(Frame frame) {
            return new FrameMessage(frame.moved(), frame.left());
        }
    }
}
