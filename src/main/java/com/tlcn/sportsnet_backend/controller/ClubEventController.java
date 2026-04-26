package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.ApiResponse;
import com.tlcn.sportsnet_backend.dto.club_event.ClubEventCreateRequest;
import com.tlcn.sportsnet_backend.dto.club_event.ClubEventFilterRequest;
import com.tlcn.sportsnet_backend.dto.club_event.ClubEventUpdateRequest;
import com.tlcn.sportsnet_backend.dto.club_event_participant.ClubEventParticipantUpdate;
import com.tlcn.sportsnet_backend.enums.ParticipantStatusEnum;
import com.tlcn.sportsnet_backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/club-event")
public class ClubEventController {
    private final ClubEventService clubEventService;
    private final FileStorageService fileStorageService;
    private final ClubEventParticipantService clubEventParticipantService;
    private final UserScheduleService userScheduleService;
    @GetMapping("/{slug}")
    public ResponseEntity<?> getEventClubInfo(@PathVariable String slug) {
        return ResponseEntity.ok(clubEventService.getEventClubInfo(slug));
    }

    @GetMapping("/all/{clubSlug}")
    public ResponseEntity<?> getAllEventClub(
            @PathVariable String clubSlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(clubEventService.getAllEventsByClubId(clubSlug, page, size));
    }

    @GetMapping("/all/my_clubs")
    public ResponseEntity<?> getAllMyClubEventClub(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(clubEventService.getAllMyClubEventClub(page, size));
    }

    @GetMapping("/all/joined")
    public ResponseEntity<?> getAllMyJoinedClubEvent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size) {
        return ResponseEntity.ok(clubEventService.getAllMyJoinedClubEvents(page, size));
    }

    @GetMapping("/nearby")
    public ResponseEntity<?> getNearestEvents(
            @RequestParam(defaultValue = "5") int top) {
        return ResponseEntity.ok(clubEventService.getNearestEvents(top));
    }

    @PostMapping("/all/public")
    public ResponseEntity<?> getAllPublicEventClub(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) String quickTimeFilter,
            @RequestParam(required = false) Boolean isFree,
            @RequestParam(required = false) BigDecimal minFee,
            @RequestParam(required = false) BigDecimal maxFee,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            @RequestBody(required = false) ClubEventFilterRequest advancedFilter) {
        return ResponseEntity.ok(clubEventService.getAllPublicEventClub(page, size, search, province, ward, quickTimeFilter, isFree, minFee, maxFee, startDate, endDate, advancedFilter));
    }
    @PostMapping
    public ResponseEntity<?> createClubEvent(@RequestBody ClubEventCreateRequest request) {
        return ResponseEntity.ok(clubEventService.createClubEvent(request));
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateClubEvent(@RequestBody ClubEventUpdateRequest request) {
        return ResponseEntity.ok(clubEventService.updateClubEvent(request));
    }

    @PostMapping("/cancel/{eventId}")
    public ResponseEntity<?> cancelClubEvent(@PathVariable String eventId) {
        clubEventService.cancelEvent(eventId);
        return ResponseEntity.ok("Hủy hoạt động thành công!");
    }

    @PostMapping("/{eventId}/cancel-join")
    public ResponseEntity<?> cancelJoin(
            @PathVariable String eventId,
            @RequestParam String reason)
    {
        return ResponseEntity.ok(clubEventParticipantService.cancelJoinEvent(eventId, reason));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("No files uploaded");
        }

        List<String> uploadedFiles = files.stream()
                .filter(f -> !f.isEmpty())
                .map(f -> fileStorageService.storeFile(f, "/club/events"))
                .toList();

        if (uploadedFiles.size() == 1) {
            // Trả về String nếu chỉ có 1 file
            return ResponseEntity.ok()
                    .body(ApiResponse.success(Map.of("fileName", uploadedFiles.getFirst())));
        } else {
            // Trả về List<String> nếu nhiều file
            return ResponseEntity.ok()
                    .body(ApiResponse.success(Map.of("fileNames", uploadedFiles)));
        }
    }

    @PostMapping("/join/{id}")
    public ResponseEntity<?> joinClubEvent(@PathVariable String id) {
        return ResponseEntity.ok(clubEventParticipantService.joinClubEvent(id));
    }

    @GetMapping("/all-participant/{id}")
    public ResponseEntity<?> getAllParticipantClubEvent(@PathVariable String id) {
        return ResponseEntity.ok(clubEventParticipantService.getAllParticipantClubEvent(id));
    }

    @PutMapping("/{idEvent}/participant/{id}/approve")
    public ResponseEntity<?> approveParticipant(@PathVariable String id,@PathVariable String idEvent) {
        return ResponseEntity.ok(clubEventParticipantService.approveParticipant(id, idEvent));
    }

    @PutMapping("/{idEvent}/participant/{id}/reject")
    public ResponseEntity<?> rejectParticipant(@PathVariable String id,@PathVariable String idEvent, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(clubEventParticipantService.rejectParticipant(id, idEvent, reason));
    }

    @PutMapping("/{idEvent}/participant/{id}")
    public ResponseEntity<?> updateParticipantStatus(@PathVariable String id, @PathVariable String idEvent, @RequestBody ClubEventParticipantUpdate status) {
        return ResponseEntity.ok(clubEventParticipantService.updateParticipant(id, idEvent, status));
    }

    @GetMapping("/can-join/{eventId}")
    public ResponseEntity<?> canJoinEvent(@PathVariable String eventId) {
        boolean canJoin = clubEventParticipantService.canJoin( eventId);

        return ResponseEntity.ok(Map.of(
                "canJoin", canJoin,
                "message", canJoin
                        ? "Bạn có thể tham gia sự kiện này"
                        : "Thời gian của hoạt động bị trùng với lịch hiện có"
        ));
    }
}
