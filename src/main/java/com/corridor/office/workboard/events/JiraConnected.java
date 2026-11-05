package com.corridor.office.workboard.events;

import java.util.UUID;

/** An admin connected the company's Jira site, or replaced its connection. */
public record JiraConnected(UUID organizationId, UUID actorId, String siteUrl, String projectKey) {}
