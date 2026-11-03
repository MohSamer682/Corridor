package com.corridor.office.presence.infrastructure;

import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.presence.application.PresenceService;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/** Tracks open sockets so they can be closed, and lets people follow only floors of their own company. */
@Configuration(proxyBeanMethods = false)
class PresenceSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Pattern FLOOR_TOPIC = Pattern.compile("/topic/floors/([0-9a-f-]{36})/frames");

    private final SocketRegistry sockets;
    // Looked up lazily: PresenceService needs the messaging template this configuration helps build.
    private final ObjectProvider<PresenceService> presence;

    PresenceSocketConfig(SocketRegistry sockets, ObjectProvider<PresenceService> presence) {
        this.sockets = sockets;
        this.presence = presence;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sockets.opened(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sockets.closed(session);
                super.afterConnectionClosed(session, status);
            }
        });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && accessor.getCommand() == StompCommand.SUBSCRIBE) {
                    refuseOtherCompanies(accessor);
                }
                return message;
            }
        });
    }

    private void refuseOtherCompanies(StompHeaderAccessor accessor) {
        var topic = FLOOR_TOPIC.matcher(String.valueOf(accessor.getDestination()));
        if (!topic.matches()) {
            return;
        }
        if (!(accessor.getUser() instanceof JwtAuthenticationToken token)
                || !presence.getObject()
                        .canWatch(CurrentUser.from(token.getToken()), UUID.fromString(topic.group(1)))) {
            throw new MessageDeliveryException("This floor isn't open to you");
        }
    }
}
