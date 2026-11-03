package com.corridor.office.spaces.api;

import com.corridor.office.common.api.ApiResult;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.spaces.application.DeskQuery;
import com.corridor.office.spaces.application.MyDesk;
import com.corridor.office.spaces.application.OfficeAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Campus")
@SecurityRequirement(name = "bearerAuth")
class DeskController {

    record OwnerRequest(UUID userId) {}

    record MyDeskView(MyDesk desk) {}

    private final DeskQuery desks;
    private final OfficeAssignmentService offices;

    DeskController(DeskQuery desks, OfficeAssignmentService offices) {
        this.desks = desks;
        this.offices = offices;
    }

    @GetMapping("/api/v1/me/desk")
    @Operation(summary = "Your private office or the desk you claimed; desk is null when you have neither")
    @ApiResponse(responseCode = "200", description = "Where Go to my desk takes you")
    MyDeskView mine(@AuthenticationPrincipal Jwt jwt) {
        return new MyDeskView(desks.mine(CurrentUser.from(jwt)).orElse(null));
    }

    @PutMapping("/api/v1/rooms/{roomId}/owner")
    @Operation(summary = "Give a private office to someone, or empty it with a null userId (HR and admins)")
    @ApiResponse(responseCode = "200", description = "Saved")
    @ApiResponse(responseCode = "403", description = "Not HR or an admin")
    @ApiResponse(responseCode = "404", description = "No such room in your company")
    @ApiResponse(responseCode = "409", description = "Not a private office, or someone already has it")
    ApiResult assignOffice(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId, @RequestBody OwnerRequest request) {
        offices.assign(CurrentUser.from(jwt), roomId, request.userId());
        return new ApiResult(roomId.toString(), request.userId() == null ? "EMPTIED" : "ASSIGNED");
    }
}
