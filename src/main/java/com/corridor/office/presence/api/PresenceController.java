package com.corridor.office.presence.api;

import com.corridor.office.common.domain.DomainException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.presence.application.PresenceService;
import com.corridor.office.presence.domain.AvatarState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** STOMP intents under /app, plus who is where over REST. */
@RestController
@Tag(name = "Presence")
@SecurityRequirement(name = "bearerAuth")
class PresenceController {

    record MoveMessage(double x, double y, double facing, boolean moving) {}

    record ErrorMessage(String code, String detail) {}

    record CountsView(Map<UUID, Integer> floors) {}

    record OccupantView(UUID userId, String displayName, double x, double y, double facing, boolean moving) {}

    private final PresenceService presence;
    private final UserDirectory directory;
    private final MessageSource copy;

    PresenceController(PresenceService presence, UserDirectory directory, MessageSource copy) {
        this.presence = presence;
        this.directory = directory;
        this.copy = copy;
    }

    private static CurrentUser person(Principal principal) {
        return CurrentUser.from(((JwtAuthenticationToken) principal).getToken());
    }

    @MessageMapping("/floors/{floorId}/enter")
    void enter(Principal principal, @DestinationVariable UUID floorId) {
        presence.enter(person(principal), floorId);
    }

    @MessageMapping("/move")
    void move(Principal principal, @Payload MoveMessage move) {
        presence.move(person(principal), move.x(), move.y(), move.facing(), move.moving());
    }

    @MessageMapping("/leave")
    void leave(Principal principal) {
        presence.leave(person(principal).userId());
    }

    @MessageExceptionHandler(DomainException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    ErrorMessage refuse(DomainException refusal) {
        return new ErrorMessage(
                refusal.code(), copy.getMessage(refusal.messageKey(), refusal.arguments(), Locale.ENGLISH));
    }

    @GetMapping("/api/v1/floors/{floorId}/occupants")
    @Operation(summary = "Who is on a floor right now, and where")
    @ApiResponse(responseCode = "200", description = "Everyone on the floor")
    @ApiResponse(responseCode = "404", description = "No such floor in your company")
    List<OccupantView> occupants(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID floorId) {
        var here = presence.occupants(CurrentUser.from(jwt), floorId);
        var people = directory.findAll(here.stream().map(AvatarState::userId).toList());
        return here.stream()
                .map(state -> new OccupantView(
                        state.userId(),
                        people.containsKey(state.userId())
                                ? people.get(state.userId()).displayName()
                                : "",
                        state.x(),
                        state.y(),
                        state.facing(),
                        state.moving()))
                .toList();
    }

    @GetMapping("/api/v1/presence/counts")
    @Operation(summary = "How many people are on each floor of your company right now")
    @ApiResponse(responseCode = "200", description = "Floor id to head count; empty floors are left out")
    CountsView counts(@AuthenticationPrincipal Jwt jwt) {
        return new CountsView(presence.counts(CurrentUser.from(jwt)));
    }
}
