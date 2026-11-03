package com.corridor.office.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** STOMP over WebSocket on /ws with the in-memory broker (ADR-004); the token is checked on CONNECT. */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@EnableScheduling
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor auth;
    private final CorsProperties cors;

    WebSocketConfig(StompAuthInterceptor auth, CorsProperties cors) {
        this.auth = auth;
        this.cors = cors;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(cors.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        var heartbeats = new ThreadPoolTaskScheduler();
        heartbeats.setThreadNamePrefix("stomp-heartbeat-");
        heartbeats.initialize();
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeats);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }
}
