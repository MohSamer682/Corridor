package com.corridor.office.conversations.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** People who can talk right now; the id depends only on who is in it. */
public record Group(UUID id, List<UUID> members) {

    static Group of(List<UUID> members) {
        var sorted = members.stream().sorted().toList();
        var key = sorted.stream().map(UUID::toString).collect(Collectors.joining(","));
        return new Group(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), sorted);
    }

    public boolean has(UUID userId) {
        return members.contains(userId);
    }
}
