package com.corridor.office.announcements.events;

import java.util.UUID;

/** Someone called a town hall. */
public record TownHallStarted(UUID organizationId, UUID actorId, String title) {}
