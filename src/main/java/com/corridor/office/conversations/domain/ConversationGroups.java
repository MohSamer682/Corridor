package com.corridor.office.conversations.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Splits a floor into groups: anyone who hears someone in a group is in it. People in focus join none. */
public final class ConversationGroups {

    private ConversationGroups() {}

    public static List<Group> of(List<Spot> spots, Set<UUID> focused) {
        var open =
                spots.stream().filter(spot -> !focused.contains(spot.userId())).toList();
        var parent = new int[open.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        // shortcut: compares every pair, ~20k checks for 200 people; bucket by tile if floors get much busier.
        for (int i = 0; i < open.size(); i++) {
            for (int j = i + 1; j < open.size(); j++) {
                var a = open.get(i);
                var b = open.get(j);
                if (HearingRange.between(a, b).hears(a, b)) {
                    parent[root(parent, i)] = root(parent, j);
                }
            }
        }
        var members = new LinkedHashMap<Integer, List<UUID>>();
        for (int i = 0; i < open.size(); i++) {
            members.computeIfAbsent(root(parent, i), r -> new ArrayList<>())
                    .add(open.get(i).userId());
        }
        return members.values().stream()
                .filter(m -> m.size() > 1)
                .map(Group::of)
                .toList();
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
