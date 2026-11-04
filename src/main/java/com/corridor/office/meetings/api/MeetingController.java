package com.corridor.office.meetings.api;

import com.corridor.office.common.api.ApiResult;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.UserDirectory.Member;
import com.corridor.office.meetings.MediaTokens.Join;
import com.corridor.office.meetings.application.MeetingMedia;
import com.corridor.office.meetings.application.MeetingService;
import com.corridor.office.meetings.application.MeetingService.MeetingView;
import com.corridor.office.meetings.application.MeetingService.ScheduleMeeting;
import com.corridor.office.spaces.SpaceCatalog.BookableRoom;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Meetings")
@SecurityRequirement(name = "bearerAuth")
class MeetingController {

    private final MeetingService meetings;
    private final MeetingMedia media;

    MeetingController(MeetingService meetings, MeetingMedia media) {
        this.meetings = meetings;
        this.media = media;
    }

    @PostMapping("/api/v1/meetings/{meetingId}/token")
    @Operation(summary = "A LiveKit token to join a meeting: invitees only, from 10 minutes before it starts")
    @ApiResponse(responseCode = "200", description = "Where to connect and the token")
    @ApiResponse(responseCode = "403", description = "You're not invited to this meeting.")
    @ApiResponse(responseCode = "409", description = "Too early, ended or cancelled")
    Join join(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId) {
        return media.join(CurrentUser.from(jwt), meetingId);
    }

    @PostMapping("/api/v1/meetings")
    @Operation(summary = "Book a room for a meeting; the same Idempotency-Key books once")
    @ApiResponse(responseCode = "201", description = "Booked")
    @ApiResponse(responseCode = "404", description = "Not a meeting or conference room of your company")
    @ApiResponse(responseCode = "409", description = "The room is booked then; the detail names the other meeting")
    @ApiResponse(responseCode = "422", description = "Title, time, length or invitees break the rules")
    ResponseEntity<MeetingView> schedule(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody ScheduleMeeting request) {
        var booked = meetings.schedule(CurrentUser.from(jwt), request, key);
        return ResponseEntity.created(URI.create("/api/v1/meetings/" + booked.id()))
                .body(booked);
    }

    @GetMapping("/api/v1/meetings")
    @Operation(summary = "Your meetings, organized or invited, that overlap [from, to)")
    @ApiResponse(responseCode = "200", description = "Meetings by start time")
    List<MeetingView> list(@AuthenticationPrincipal Jwt jwt, @RequestParam Instant from, @RequestParam Instant to) {
        return meetings.list(CurrentUser.from(jwt), from, to);
    }

    @GetMapping("/api/v1/meeting-rooms")
    @Operation(summary = "Rooms meetings can be booked in, by building, floor and name")
    @ApiResponse(responseCode = "200", description = "Meeting and conference rooms")
    List<BookableRoom> rooms(@AuthenticationPrincipal Jwt jwt) {
        return meetings.bookableRooms(CurrentUser.from(jwt));
    }

    @GetMapping("/api/v1/meeting-invitees")
    @Operation(summary = "People in your company you can invite, by name")
    @ApiResponse(responseCode = "200", description = "Active people with their team")
    List<Member> invitees(@AuthenticationPrincipal Jwt jwt) {
        return meetings.invitable(CurrentUser.from(jwt));
    }

    @GetMapping("/api/v1/meeting-rooms/{roomId}/meetings")
    @Operation(summary = "What is booked in a room during [from, to)")
    @ApiResponse(responseCode = "200", description = "Bookings by start time")
    List<MeetingView> inRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return meetings.inRoom(CurrentUser.from(jwt), roomId, from, to);
    }

    @PatchMapping("/api/v1/meetings/{meetingId}")
    @Operation(summary = "Change a meeting's room, time, title or invitees (organizer only)")
    @ApiResponse(responseCode = "200", description = "Saved")
    @ApiResponse(responseCode = "403", description = "Not the organizer")
    @ApiResponse(responseCode = "409", description = "The new slot clashes, or the meeting is over")
    MeetingView reschedule(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId, @RequestBody ScheduleMeeting request) {
        return meetings.reschedule(CurrentUser.from(jwt), meetingId, request);
    }

    @PostMapping("/api/v1/meetings/{meetingId}/cancellation")
    @Operation(summary = "Cancel a meeting and free its room (organizer only)")
    @ApiResponse(responseCode = "200", description = "Cancelled")
    @ApiResponse(responseCode = "403", description = "Not the organizer")
    ApiResult cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID meetingId) {
        meetings.cancel(CurrentUser.from(jwt), meetingId);
        return new ApiResult(meetingId.toString(), "CANCELLED");
    }
}
