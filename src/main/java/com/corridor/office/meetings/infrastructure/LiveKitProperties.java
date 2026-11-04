package com.corridor.office.meetings.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where LiveKit runs and the key pair its tokens are signed with; the secret stays on the server. */
@ConfigurationProperties("app.livekit")
public record LiveKitProperties(
        @DefaultValue("ws://localhost:7880") String url,
        @DefaultValue("") String apiKey,
        @DefaultValue("") String apiSecret) {}
