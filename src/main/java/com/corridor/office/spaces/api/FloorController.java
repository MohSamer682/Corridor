package com.corridor.office.spaces.api;

import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.spaces.application.CampusQuery;
import com.corridor.office.spaces.application.FloorLayoutView;
import com.corridor.office.spaces.application.RoomView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/v1/floors/{id}")
@Tag(name = "Campus")
@SecurityRequirement(name = "bearerAuth")
class FloorController {

    private final CampusQuery campus;

    FloorController(CampusQuery campus) {
        this.campus = campus;
    }

    @GetMapping("/layout")
    @Operation(summary = "A floor's grid, decor, rooms and seats; send the ETag back in If-None-Match")
    @ApiResponse(responseCode = "200", description = "The current layout, with ETag \"floorId:version\"")
    @ApiResponse(responseCode = "304", description = "The layout hasn't changed since that ETag")
    @ApiResponse(responseCode = "404", description = "No such floor in your company")
    ResponseEntity<FloorLayoutView> layout(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, WebRequest request) {
        var viewer = CurrentUser.from(jwt);
        var etag = "\"" + id + ":" + campus.layoutVersion(viewer, id) + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok().eTag(etag).body(campus.layout(viewer, id));
    }

    @GetMapping("/rooms")
    @Operation(summary = "Rooms and open areas on a floor, with how many seats each has")
    @ApiResponse(responseCode = "200", description = "Rooms ordered by name")
    @ApiResponse(responseCode = "404", description = "No such floor in your company")
    List<RoomView> rooms(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return campus.rooms(CurrentUser.from(jwt), id);
    }
}
