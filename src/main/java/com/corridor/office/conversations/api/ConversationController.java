package com.corridor.office.conversations.api;

import com.corridor.office.common.api.CursorResponse;
import com.corridor.office.common.domain.DomainException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.application.ConversationService;
import com.corridor.office.conversations.application.ConversationService.FocusView;
import com.corridor.office.conversations.application.MessageService;
import com.corridor.office.conversations.application.MessageService.MessageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Focus and chat over STOMP under /app/conversations, history over REST. */
@RestController
@Tag(name = "Conversations")
@SecurityRequirement(name = "bearerAuth")
class ConversationController {

    record FocusMessage(boolean on) {}

    record ChatMessage(String body) {}

    record ErrorMessage(String code, String detail) {}

    private final ConversationService conversations;
    private final MessageService messages;
    private final MessageSource copy;

    ConversationController(ConversationService conversations, MessageService messages, MessageSource copy) {
        this.conversations = conversations;
        this.messages = messages;
        this.copy = copy;
    }

    private static CurrentUser person(Principal principal) {
        return CurrentUser.from(((JwtAuthenticationToken) principal).getToken());
    }

    @MessageMapping("/conversations/focus")
    void focus(Principal principal, @Payload FocusMessage focus) {
        conversations.focus(person(principal), focus.on());
    }

    @MessageMapping("/conversations/messages")
    void send(Principal principal, @Payload ChatMessage chat) {
        messages.send(person(principal), chat.body());
    }

    @MessageExceptionHandler(DomainException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    ErrorMessage refuse(DomainException refusal) {
        return new ErrorMessage(
                refusal.code(), copy.getMessage(refusal.messageKey(), refusal.arguments(), Locale.ENGLISH));
    }

    @GetMapping("/api/v1/floors/{floorId}/messages")
    @Operation(summary = "Chat you received on a floor, newest first, 50 at a time")
    @ApiResponse(responseCode = "200", description = "Messages; pass nextCursor as before for older ones")
    CursorResponse<MessageView> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID floorId,
            @RequestParam(required = false) String before) {
        return messages.history(CurrentUser.from(jwt), floorId, before);
    }

    @GetMapping("/api/v1/floors/{floorId}/focus")
    @Operation(summary = "Who is in focus mode on a floor right now")
    @ApiResponse(responseCode = "200", description = "User ids of people in focus")
    FocusView focused(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID floorId) {
        return conversations.focusOn(CurrentUser.from(jwt), floorId);
    }
}
