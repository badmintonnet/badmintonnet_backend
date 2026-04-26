package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.ApiResponse;
import com.tlcn.sportsnet_backend.dto.club.ClubCreateRequest;
import com.tlcn.sportsnet_backend.dto.club.JoinClubRequest;
import com.tlcn.sportsnet_backend.enums.ClubMemberStatusEnum;
import com.tlcn.sportsnet_backend.service.ClubMemberService;
import com.tlcn.sportsnet_backend.service.ClubService;
import com.tlcn.sportsnet_backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clubs")
public class ClubController {
    private final ClubService clubService;
    private final FileStorageService fileStorageService;
    private final ClubMemberService clubMemberService;

    @GetMapping("/{slug}")
    public ResponseEntity<?> getClubInformation(@PathVariable String slug) {
        return ResponseEntity.ok(clubService.getClubInformation(slug));
    }

    @GetMapping("/all_public")
    public ResponseEntity<?> getAllPublicClubs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) List<String> selectedLevels,
            @RequestParam(required = false) String reputationSort,
            @RequestParam(required = false) List<String> facilityNames){
        return ResponseEntity.ok(clubService.getAllClubPublic(page, size, search, province, ward, selectedLevels, reputationSort, facilityNames));
    }

    @GetMapping("/my_clubs/all")
    public ResponseEntity<?> getAllMyClubs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size) {
        return ResponseEntity.ok(clubService.getAllMyClub(page, size));
    }

    @GetMapping("/nearby")
    public ResponseEntity<?> getNearestClubs(@RequestParam(defaultValue = "5") int top) {
        return ResponseEntity.ok(clubService.getNearestClubs(top));
    }

    @GetMapping("/my_clubs/{slug}")
    public ResponseEntity<?> getMyClubInformation(
                                                   @PathVariable String slug) {
        return ResponseEntity.ok(clubService.getMyClubInformation(slug));
    }

    @GetMapping("/my_clubs/{id}/member")
    public ResponseEntity<?> getMyClubMember(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "100") int size,
                                             @RequestParam(defaultValue = "APPROVED") ClubMemberStatusEnum status,
                                             @PathVariable String id) {
        return ResponseEntity.ok(clubMemberService.getMembers(page,size, status, id));
    }

    @GetMapping("/my_clubs/{id}/member/{memberId}/schedule")
    public ResponseEntity<?> getMyClubMemberSchedule(@PathVariable String id, @PathVariable String memberId) {
        return ResponseEntity.ok(clubMemberService.getSchedule(id,memberId));
    }

    @GetMapping("/my_clubs/{id}/guest")
    public ResponseEntity<?> getMyClubGuest(@PathVariable String id){
        return ResponseEntity.ok(clubMemberService.getAllGuest(id));

    }

    @PostMapping
    public ResponseEntity<?> createClub(@RequestBody ClubCreateRequest request) {
        return ResponseEntity.ok(clubService.createClub(request));
    }

    @PutMapping("/{clubId}")
    public ResponseEntity<?> updateClub(@PathVariable String clubId, @RequestBody ClubCreateRequest request) {
        return ResponseEntity.ok(clubService.updateClub(clubId, request));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        String uploadedFile = fileStorageService.storeFile(file, "/club/logo");

        return ResponseEntity.ok()
                .body(ApiResponse.success(Map.of("fileName", uploadedFile)));
    }

    @PostMapping("/{clubId}/join")
    public ResponseEntity<?> joinClub(@PathVariable String clubId,  @RequestBody JoinClubRequest request) {
        return ResponseEntity.ok(clubMemberService.joinClub(clubId, request.getNotification()));
    }

    @PostMapping("/my_clubs/{clubId}/member/{memberId}/approve")
    public ResponseEntity<?> approveMember(
            @PathVariable String clubId,
            @PathVariable String memberId,
            @RequestParam boolean approve,
            @RequestParam(required = false) String reason) {

        clubMemberService.approveMember(clubId, memberId, approve, reason);

        if (approve) {
            return ResponseEntity.ok("Member approved successfully");
        } else {
            return ResponseEntity.ok("Member rejected successfully with reason: " + reason);
        }
    }

    @PostMapping("/{clubId}//{memberId}/ban")
    public ResponseEntity<?> banMember(@PathVariable String clubId, @PathVariable String memberId) {
        clubMemberService.banMember(clubId, memberId);
        return ResponseEntity.ok().body("Ban success");
    }

    @GetMapping("/my_clubs/{clubId}/detail_member/{id}")
    public ResponseEntity<?> getMyClubMember(@PathVariable String clubId,@PathVariable String id) {
        return ResponseEntity.ok(clubMemberService.getDetailMember(clubId, id));
    }

    @PutMapping("/my_clubs/{clubId}/members/{memberId}/verify")
    public ResponseEntity<?> verifyMember(@PathVariable String clubId,@PathVariable String memberId) {
        clubMemberService.verifyMember(clubId, memberId);
        return ResponseEntity.ok("Verify success");
    }

    @DeleteMapping("/my_clubs/{clubId}/out")
    public ResponseEntity<?> outClub(@PathVariable String clubId){
        return ResponseEntity.ok(clubMemberService.outClub(clubId));
    }
//    @PostMapping("/update")
//    public String update() {
//        clubService.calculateAllClubReputationOnStartup();
//        return "Update thành công";
//    }
}
