package com.corridor.office.conversations.application;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** STUN and TURN for WebRTC; TURN is offered only when a coturn shared secret is set. */
@ConfigurationProperties("app.media")
public record MediaProperties(
        @DefaultValue("stun:stun.l.google.com:19302") List<String> stunUrls,
        @DefaultValue List<String> turnUrls,
        @DefaultValue("") String turnSecret) {}
