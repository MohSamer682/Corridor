package com.corridor.office.announcements.events;

import java.util.UUID;

/** Someone broadcast an announcement; {@code teamName} is null when it went to everyone. */
public record AnnouncementPublished(
        UUID announcementId, UUID organizationId, UUID actorId, String teamName, String body, int audience) {}
