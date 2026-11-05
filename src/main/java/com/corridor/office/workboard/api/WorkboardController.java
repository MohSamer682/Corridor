package com.corridor.office.workboard.api;

import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.workboard.application.BoardQuery.BoardView;
import com.corridor.office.workboard.application.JiraBoardService;
import com.corridor.office.workboard.application.JiraBoardService.Connect;
import com.corridor.office.workboard.application.JiraBoardService.ConnectionView;
import com.corridor.office.workboard.application.JiraWebhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Jira board")
class WorkboardController {

    private final JiraBoardService boards;
    private final JiraWebhook webhook;

    WorkboardController(JiraBoardService boards, JiraWebhook webhook) {
        this.boards = boards;
        this.webhook = webhook;
    }

    @PutMapping("/api/v1/integrations/jira")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Connect the company's Jira project with an API token (admins)")
    @ApiResponse(responseCode = "200", description = "Connected; the board is cached")
    @ApiResponse(responseCode = "403", description = "Only admins connect Jira")
    @ApiResponse(responseCode = "422", description = "Not an Atlassian site, or Jira refused the token")
    ConnectionView connect(@AuthenticationPrincipal Jwt jwt, @RequestBody Connect request) {
        return boards.connect(CurrentUser.from(jwt), request);
    }

    @GetMapping("/api/v1/integrations/jira")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The Jira connection, without the token (admins)")
    @ApiResponse(responseCode = "200", description = "Connected")
    @ApiResponse(responseCode = "204", description = "Not connected")
    ResponseEntity<ConnectionView> connection(@AuthenticationPrincipal Jwt jwt) {
        return boards.connection(CurrentUser.from(jwt))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/api/v1/board")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The sprint board, from the cache")
    @ApiResponse(responseCode = "200", description = "Four columns; stale when Jira couldn't be reached")
    @ApiResponse(responseCode = "204", description = "Jira isn't connected")
    ResponseEntity<BoardView> board(@AuthenticationPrincipal Jwt jwt) {
        return boards.board(CurrentUser.from(jwt))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/api/v1/integrations/jira/webhook/{organizationId}")
    @Operation(summary = "Jira's issue events, signed with the shared secret")
    @ApiResponse(responseCode = "204", description = "Applied")
    @ApiResponse(responseCode = "401", description = "Missing or wrong signature")
    ResponseEntity<Void> receive(
            @PathVariable UUID organizationId,
            @RequestHeader(name = "X-Hub-Signature", required = false) String signature,
            @RequestBody String body) {
        var status = webhook.receive(organizationId, signature, body) ? HttpStatus.NO_CONTENT : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).build();
    }
}
