package com.corridor.office.identity.domain.events;

import java.util.UUID;

/** Team names are null when the person had no team before, or has none after. */
public record UserTeamChanged(
        UUID userId, UUID organizationId, UUID actorId, String name, String fromTeam, String toTeam) {}
