package com.corridor.office.conversations.api;

import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.application.GroupMedia;
import com.corridor.office.meetings.MediaTokens.Join;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Conversations")
@SecurityRequirement(name = "bearerAuth")
class GroupMediaController {

    private final GroupMedia media;

    GroupMediaController(GroupMedia media) {
        this.media = media;
    }

    @GetMapping("/api/v1/conversations/media")
    @Operation(summary = "A LiveKit token for your conversation, when it has five or more people")
    @ApiResponse(responseCode = "200", description = "Where to connect and the token")
    @ApiResponse(responseCode = "409", description = "Your conversation talks peer to peer, or you're in none")
    Join join(@AuthenticationPrincipal Jwt jwt) {
        return media.join(CurrentUser.from(jwt));
    }
}
