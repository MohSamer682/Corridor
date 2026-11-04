package com.corridor.office.presence.domain;

import java.util.List;
import java.util.UUID;

/**
 * What changed on a floor in one tick: avatars that moved, people who left, people who settled, and people whose
 * reported position was refused (they get their last good position back).
 */
public record Frame(List<AvatarState> moved, List<UUID> left, List<Settled> settled, List<AvatarState> corrected) {

    public static final Frame EMPTY = new Frame(List.of(), List.of(), List.of(), List.of());

    public boolean isEmpty() {
        return moved.isEmpty() && left.isEmpty() && settled.isEmpty() && corrected.isEmpty();
    }
}
