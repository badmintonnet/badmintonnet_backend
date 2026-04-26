package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.ApiResponse;
import com.tlcn.sportsnet_backend.dto.account.UpdateProfileRequest;
import com.tlcn.sportsnet_backend.dto.player_rating.PlayerRatingCreateRequest;
import com.tlcn.sportsnet_backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final PlayerRatingService playerRatingService;
    private final FileStorageService fileStorageService;
    private final ReputationHistoryService reputationHistoryService;
    private final UserScheduleService userScheduleService;
    private final PlayerTournamentHistoryService historyService;
    private final TournamentService tournamentService;
    private final FriendshipService friendshipService;

    @GetMapping
    public ResponseEntity<?> getAccount() {
        return ResponseEntity.ok(accountService.getAccount());
    }

    @GetMapping("/other-profile/{slug}")
    public ResponseEntity<?> getOtherAccount(@PathVariable String slug) {
        return ResponseEntity.ok(accountService.getOtherAccount(slug));
    }

    @PutMapping("/protect_profile")
    public ResponseEntity<?> protectProfile(){
        return ResponseEntity.ok(accountService.protectProfile());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(accountService.updateProfile(request));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        String uploadedFile = fileStorageService.storeFile(file, "/avatar");

        return ResponseEntity.ok()
                .body(ApiResponse.success(Map.of("fileName", uploadedFile)));
    }
    @GetMapping("/get-all-club-id")
    public ResponseEntity<?> getAllClubID() {
        return ResponseEntity.ok(accountService.getAllClubID());
    }

    @PostMapping("/player-rating")
    public ResponseEntity<?> createPlayerRating(@RequestBody PlayerRatingCreateRequest request) {
        return ResponseEntity.ok(playerRatingService.createPlayerRating(request));
    }

    @GetMapping("/player-rating")
    public ResponseEntity<?> getPlayerRating() {
        return ResponseEntity.ok(playerRatingService.getPlayerRating());
    }

    @GetMapping("/player-rating/{id}")
    public ResponseEntity<?> getPlayerRating(@PathVariable String id) {return ResponseEntity.ok(playerRatingService.getPlayerRatingById(id));}

    @GetMapping("/reputation-history")
    public ResponseEntity<?> getReputationHistory() {
        return ResponseEntity.ok(reputationHistoryService.getAll());
    }

    @PostMapping("/create-schedule")
    public ResponseEntity<?> createSchedule(){
        userScheduleService.createAll();
        return ResponseEntity.ok("Đã khợi tạo thành công");
    }

    @GetMapping("/schedule")
    public ResponseEntity<?> getSchedule(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userScheduleService.getSchedule(page, size));
    }

    @GetMapping("/tournament-history/{id}")
    public ResponseEntity<?> getHistoryTournamentByPlayer(@PathVariable String id) {
        return ResponseEntity.ok(historyService.getHistoryByPlayer(id));
    }


    @GetMapping("/nearby-users")
    public ResponseEntity<?> getNearbyUsers(
            @RequestParam(defaultValue = "5") int top) {
        return ResponseEntity.ok(friendshipService.getNearbyUsers(top));
    }
}
