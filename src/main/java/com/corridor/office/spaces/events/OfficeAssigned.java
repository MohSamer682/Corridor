package com.corridor.office.spaces.events;

import java.util.UUID;

/** HR gave a private office to someone, or emptied it when {@code ownerId} is null. */
public record OfficeAssigned(UUID roomId, UUID organizationId, UUID actorId, String roomName, UUID ownerId) {}
