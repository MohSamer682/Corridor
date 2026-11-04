package com.corridor.office.conversations.application;

import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.domain.ConversationGroups;
import com.corridor.office.conversations.domain.Group;
import com.corridor.office.conversations.domain.MediaRoute;
import com.corridor.office.conversations.domain.Spot;
import com.corridor.office.presence.events.FloorTicked;
import com.corridor.office.presence.events.FloorTicked.Standing;
import com.corridor.office.spaces.SpaceCatalog;
import com.corridor.office.spaces.SpaceCatalog.PrivateRoom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Who can talk to whom, per floor, in memory like presence (ADR-006). Groups are rebuilt on the floor's tick
 * thread after positions move, and right away when someone turns focus on or off.
 */
@Service
public class ConversationService {

    /**
     * Sent to a person whenever their group changes; {@code groupId} is null when they are in none. {@code route}
     * says how voice travels: PEER_TO_PEER, or SFU through LiveKit.
     */
    public record GroupView(UUID groupId, List<UUID> members, String route) {}

    /** Who is in focus on a floor, so walking up to them can explain why nothing happens. */
    public record FocusView(List<UUID> focused) {}

    private static final class LiveFloor {
        final UUID organizationId;
        final List<PrivateRoom> rooms;
        List<Standing> everyone = List.of();
        Map<UUID, Group> groupOf = Map.of();

        LiveFloor(UUID organizationId, List<PrivateRoom> rooms) {
            this.organizationId = organizationId;
            this.rooms = rooms;
        }
    }

    private final Map<UUID, LiveFloor> floors = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> floorOf = new ConcurrentHashMap<>();
    private final Set<UUID> focused = ConcurrentHashMap.newKeySet();
    private final SpaceCatalog spaces;
    private final SimpMessagingTemplate sockets;

    ConversationService(SpaceCatalog spaces, SimpMessagingTemplate sockets) {
        this.spaces = spaces;
        this.sockets = sockets;
    }

    @EventListener
    void on(FloorTicked tick) {
        var floor = floors.computeIfAbsent(
                tick.floorId(),
                id -> new LiveFloor(tick.organizationId(), spaces.privateRooms(tick.organizationId(), id)));
        synchronized (floor) {
            var here = new HashSet<UUID>();
            tick.everyone().forEach(person -> {
                here.add(person.userId());
                floorOf.put(person.userId(), tick.floorId());
            });
            floor.everyone.stream()
                    .map(Standing::userId)
                    .filter(userId -> !here.contains(userId))
                    .forEach(userId -> {
                        // Leaving the office ends focus mode; walking to another floor keeps it.
                        if (floorOf.remove(userId, tick.floorId())) {
                            focused.remove(userId);
                        }
                    });
            floor.everyone = tick.everyone();
            regroup(floor);
        }
        if (tick.everyone().isEmpty()) {
            floors.remove(tick.floorId(), floor);
        }
    }

    /** Turns focus mode on or off; on takes you out of your group at once. */
    public void focus(CurrentUser person, boolean on) {
        var changed = on ? focused.add(person.userId()) : focused.remove(person.userId());
        var floorId = floorOf.get(person.userId());
        var floor = floorId == null ? null : floors.get(floorId);
        if (!changed || floor == null) {
            return;
        }
        synchronized (floor) {
            regroup(floor);
        }
        sockets.convertAndSend("/topic/floors/" + floorId + "/focus", focusOn(floor));
    }

    /** Who is in focus on a floor of the viewer's company; another company's floor looks empty. */
    public FocusView focusOn(CurrentUser viewer, UUID floorId) {
        var floor = floors.get(floorId);
        return floor == null || !floor.organizationId.equals(viewer.organizationId())
                ? new FocusView(List.of())
                : focusOn(floor);
    }

    private FocusView focusOn(LiveFloor floor) {
        synchronized (floor) {
            return new FocusView(floor.everyone.stream()
                    .map(Standing::userId)
                    .filter(focused::contains)
                    .toList());
        }
    }

    public boolean inFocus(UUID userId) {
        return focused.contains(userId);
    }

    public Optional<UUID> floorOf(UUID userId) {
        return Optional.ofNullable(floorOf.get(userId));
    }

    public Optional<Group> groupOf(UUID userId) {
        var floorId = floorOf.get(userId);
        var floor = floorId == null ? null : floors.get(floorId);
        if (floor == null) {
            return Optional.empty();
        }
        synchronized (floor) {
            return Optional.ofNullable(floor.groupOf.get(userId));
        }
    }

    private void regroup(LiveFloor floor) {
        var spots = floor.everyone.stream()
                .map(person -> new Spot(person.userId(), person.x(), person.y(), roomAt(floor, person)))
                .toList();
        var next = new HashMap<UUID, Group>();
        ConversationGroups.of(spots, focused).forEach(group -> group.members().forEach(id -> next.put(id, group)));
        var touched = new HashSet<>(floor.groupOf.keySet());
        touched.addAll(next.keySet());
        for (var userId : touched) {
            var before = floor.groupOf.get(userId);
            var after = next.get(userId);
            if (before == null || after == null || !before.id().equals(after.id())) {
                var view = after == null
                        ? new GroupView(null, List.of(), null)
                        : new GroupView(
                                after.id(),
                                after.members(),
                                MediaRoute.forGroup(after).name());
                sockets.convertAndSendToUser(userId.toString(), "/queue/conversation", view);
            }
        }
        floor.groupOf = next;
    }

    private static UUID roomAt(LiveFloor floor, Standing person) {
        return floor.rooms.stream()
                .filter(room -> room.contains(person.x(), person.y()))
                .map(PrivateRoom::id)
                .findFirst()
                .orElse(null);
    }
}
