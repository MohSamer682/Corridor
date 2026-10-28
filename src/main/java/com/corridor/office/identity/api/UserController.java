package com.corridor.office.identity.api;

import com.corridor.office.common.api.ApiResult;
import com.corridor.office.common.api.ETags;
import com.corridor.office.common.api.PageResponse;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.application.NewUser;
import com.corridor.office.identity.application.UserAccessService;
import com.corridor.office.identity.application.UserChanges;
import com.corridor.office.identity.application.UserService;
import com.corridor.office.identity.application.UserSummary;
import com.corridor.office.identity.application.UserView;
import com.corridor.office.identity.domain.UserStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "People")
@SecurityRequirement(name = "bearerAuth")
class UserController {

    private final UserService users;
    private final UserAccessService access;

    UserController(UserService users, UserAccessService access) {
        this.users = users;
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "List people in your company, filtered and paged (admins only)")
    @ApiResponse(responseCode = "200", description = "A page of people")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    PageResponse<UserSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return users.list(CurrentUser.from(jwt), status, teamId, query, pageable);
    }

    @PostMapping
    @Operation(summary = "Add a person with a first password")
    @ApiResponse(responseCode = "201", description = "Added; Location points at the new person")
    @ApiResponse(responseCode = "409", description = "A user with this email already exists")
    @ApiResponse(responseCode = "422", description = "Some fields need attention")
    ResponseEntity<UserView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUserRequest request) {
        var created = users.create(
                CurrentUser.from(jwt),
                new NewUser(
                        request.email(),
                        request.displayName(),
                        request.jobTitle(),
                        request.role(),
                        request.teamId(),
                        request.password()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id()))
                .eTag(ETags.of(created.version()))
                .body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One person, with their version as the ETag")
    @ApiResponse(responseCode = "200", description = "The person")
    @ApiResponse(responseCode = "404", description = "Not in your company")
    ResponseEntity<UserView> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var user = users.get(CurrentUser.from(jwt), id);
        return ResponseEntity.ok().eTag(ETags.of(user.version())).body(user);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change name, job title, role or team; send the ETag back as If-Match")
    @ApiResponse(responseCode = "200", description = "Saved")
    @ApiResponse(responseCode = "409", description = "Someone else changed this person, or the change is not allowed")
    @ApiResponse(responseCode = "428", description = "If-Match is missing")
    ResponseEntity<UserView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateUserRequest request) {
        var expectedVersion = ETags.version(ifMatch, "users.reloadFirst");
        var updated = users.update(
                CurrentUser.from(jwt),
                id,
                expectedVersion,
                new UserChanges(request.displayName(), request.jobTitle(), request.role(), request.teamId()));
        return ResponseEntity.ok().eTag(ETags.of(updated.version())).body(updated);
    }

    @PostMapping("/{id}/suspension")
    @Operation(summary = "Suspend a person and end their sessions")
    @ApiResponse(responseCode = "200", description = "Suspended")
    @ApiResponse(responseCode = "409", description = "Yourself or the last active admin")
    ApiResult suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        access.suspend(CurrentUser.from(jwt), id);
        return new ApiResult(id.toString(), "SUSPENDED");
    }

    @DeleteMapping("/{id}/suspension")
    @Operation(summary = "Let a suspended person sign in again")
    @ApiResponse(responseCode = "200", description = "Active again")
    ApiResult reactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        access.reactivate(CurrentUser.from(jwt), id);
        return new ApiResult(id.toString(), "ACTIVE");
    }

    @PostMapping("/{id}/approval")
    @Operation(summary = "Approve a sign-up request with a role and team (HR or admins)")
    @ApiResponse(responseCode = "200", description = "Approved; the person can sign in")
    @ApiResponse(responseCode = "403", description = "Only an admin can grant admin access")
    @ApiResponse(responseCode = "409", description = "The request was already handled")
    ApiResult approve(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ApprovalRequest request) {
        access.approve(CurrentUser.from(jwt), id, request.role(), request.teamId());
        return new ApiResult(id.toString(), "ACTIVE");
    }

    @DeleteMapping("/{id}/approval")
    @Operation(summary = "Decline a sign-up request and delete it")
    @ApiResponse(responseCode = "200", description = "Declined")
    @ApiResponse(responseCode = "409", description = "The request was already handled")
    ApiResult decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        access.decline(CurrentUser.from(jwt), id);
        return new ApiResult(id.toString(), "DECLINED");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a person from Corridor")
    @ApiResponse(responseCode = "200", description = "Removed")
    @ApiResponse(responseCode = "409", description = "Yourself or the last active admin")
    ApiResult remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        access.remove(CurrentUser.from(jwt), id);
        return new ApiResult(id.toString(), "REMOVED");
    }
}
