package com.corridor.office.identity.api;

import com.corridor.office.common.api.ApiResult;
import com.corridor.office.common.api.ETags;
import com.corridor.office.common.api.PageResponse;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.application.TeamService;
import com.corridor.office.identity.application.TeamView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams")
@SecurityRequirement(name = "bearerAuth")
class TeamController {

    private final TeamService teams;

    TeamController(TeamService teams) {
        this.teams = teams;
    }

    @GetMapping
    @Operation(summary = "Teams in your company with member counts")
    @ApiResponse(responseCode = "200", description = "A page of teams")
    PageResponse<TeamView> list(@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 100) Pageable pageable) {
        return teams.list(CurrentUser.from(jwt), pageable);
    }

    @PostMapping
    @Operation(summary = "Add a team (admins only)")
    @ApiResponse(responseCode = "201", description = "Added; Location points at the new team")
    @ApiResponse(responseCode = "409", description = "A team with this name already exists")
    ResponseEntity<TeamView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TeamRequest request) {
        var created = teams.create(CurrentUser.from(jwt), request.name(), request.color());
        return ResponseEntity.created(URI.create("/api/v1/teams/" + created.id()))
                .eTag(ETags.of(created.version()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename or recolor a team; send its version back as If-Match")
    @ApiResponse(responseCode = "200", description = "Saved")
    @ApiResponse(responseCode = "409", description = "Someone else changed this team, or the name is taken")
    @ApiResponse(responseCode = "428", description = "If-Match is missing")
    ResponseEntity<TeamView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody TeamRequest request) {
        var updated = teams.update(
                CurrentUser.from(jwt),
                id,
                ETags.version(ifMatch, "teams.reloadFirst"),
                request.name(),
                request.color());
        return ResponseEntity.ok().eTag(ETags.of(updated.version())).body(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an empty team")
    @ApiResponse(responseCode = "200", description = "Deleted")
    @ApiResponse(responseCode = "409", description = "The team still has members")
    ApiResult delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        teams.delete(CurrentUser.from(jwt), id);
        return new ApiResult(id.toString(), "REMOVED");
    }
}
