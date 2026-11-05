package com.corridor.office.announcements.api;

import com.corridor.office.announcements.application.AnnouncementService;
import com.corridor.office.announcements.application.AnnouncementService.AnnouncementView;
import com.corridor.office.announcements.application.AnnouncementService.Sent;
import com.corridor.office.announcements.application.TownHallService;
import com.corridor.office.announcements.application.TownHallService.TownHall;
import com.corridor.office.common.api.ApiResult;
import com.corridor.office.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Announcements")
@SecurityRequirement(name = "bearerAuth")
class AnnouncementController {

    record Broadcast(UUID teamId, String body) {}

    record CallTownHall(String title) {}

    private final AnnouncementService announcements;
    private final TownHallService townHall;

    AnnouncementController(AnnouncementService announcements, TownHallService townHall) {
        this.announcements = announcements;
        this.townHall = townHall;
    }

    @PostMapping("/api/v1/announcements")
    @Operation(summary = "Broadcast to everyone, or to one team (managers, HR and admins)")
    @ApiResponse(responseCode = "200", description = "Sent; how many it is for and how many were online")
    @ApiResponse(responseCode = "403", description = "Employees can't broadcast")
    @ApiResponse(responseCode = "422", description = "Write between 1 and 1,000 characters")
    Sent publish(@AuthenticationPrincipal Jwt jwt, @RequestBody Broadcast broadcast) {
        return announcements.publish(CurrentUser.from(jwt), broadcast.teamId(), broadcast.body());
    }

    @GetMapping("/api/v1/announcements/unread")
    @Operation(summary = "Announcements for you that you haven't dismissed, from the last 7 days")
    @ApiResponse(responseCode = "200", description = "Oldest first")
    List<AnnouncementView> unread(@AuthenticationPrincipal Jwt jwt) {
        return announcements.unread(CurrentUser.from(jwt));
    }

    @PostMapping("/api/v1/announcements/{id}/read")
    @Operation(summary = "Dismiss an announcement")
    @ApiResponse(responseCode = "200", description = "Dismissed")
    ApiResult read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        announcements.markRead(CurrentUser.from(jwt), id);
        return new ApiResult(id.toString(), "READ");
    }

    @GetMapping("/api/v1/town-hall")
    @Operation(summary = "The live town hall, if there is one")
    @ApiResponse(responseCode = "200", description = "The town hall")
    @ApiResponse(responseCode = "204", description = "None is live")
    ResponseEntity<TownHall> current(@AuthenticationPrincipal Jwt jwt) {
        return townHall.current(CurrentUser.from(jwt))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/api/v1/town-hall")
    @Operation(summary = "Call a town hall; everyone is invited to walk to the hall")
    @ApiResponse(responseCode = "200", description = "Live")
    @ApiResponse(responseCode = "409", description = "One is already live")
    TownHall start(@AuthenticationPrincipal Jwt jwt, @RequestBody CallTownHall call) {
        return townHall.start(CurrentUser.from(jwt), call.title());
    }

    @DeleteMapping("/api/v1/town-hall")
    @Operation(summary = "End the town hall")
    @ApiResponse(responseCode = "200", description = "Ended")
    ApiResult end(@AuthenticationPrincipal Jwt jwt) {
        townHall.end(CurrentUser.from(jwt));
        return new ApiResult("town-hall", "ENDED");
    }
}
