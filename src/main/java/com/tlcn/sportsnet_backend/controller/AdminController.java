package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.ApiResponse;
import com.tlcn.sportsnet_backend.dto.dashboard.DashboardPeriod;
import com.tlcn.sportsnet_backend.dto.tournament.TournamentCreateRequest;
import com.tlcn.sportsnet_backend.entity.Club;
import com.tlcn.sportsnet_backend.entity.Tournament;
import com.tlcn.sportsnet_backend.enums.ClubStatusEnum;
import com.tlcn.sportsnet_backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final TournamentService tournamentService;
    private final FileStorageService fileStorageService;
    private final DashboardService dashboardService;

    @GetMapping("/dashboard/overview")
    public ResponseEntity<?> getDashboardOverview(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "DAY") DashboardPeriod period) {
        return ResponseEntity.ok(dashboardService.getAdminOverview(from, to, period));
    }

    @GetMapping("/clubs/all")
    public ResponseEntity<?> getAllClubs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminService.getAllClubs(page, size));
    }


    @PutMapping("/clubs/{id}/status")
    public ResponseEntity<?> updateClubStatus(@PathVariable String id, @RequestParam ClubStatusEnum newStatus) {
        adminService.updateClubStatus(id, newStatus);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clubs/{id}")
    public ResponseEntity<?> deleteClub(@PathVariable String id) {
        adminService.deleteClub(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events/all")
    public ResponseEntity<?> getAllEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminService.getAllAdminEvent(page, size));
    }

    @GetMapping("users/all")
    public ResponseEntity<?> getAllAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminService.getAllAccounts(page, size));
    }

    @PutMapping("/users/{id}/ban")
    public ResponseEntity<?> updateAccountStatus(@PathVariable String id) {
        adminService.updateAccountStatus(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tournament")
    public ResponseEntity<?> createTournament(@RequestBody TournamentCreateRequest tournament) {
        return ResponseEntity.ok(tournamentService.createTournament(tournament));

    }
    @PostMapping("/tournament/upload")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("No files uploaded");
        }

        List<String> uploadedFiles = files.stream()
                .filter(f -> !f.isEmpty())
                .map(f -> fileStorageService.storeFile(f, "/tournament"))
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

    @GetMapping("/tournament/all")
    public ResponseEntity<?> getTournaments(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminService.getAllAdminTournaments(page, size));
    }
}
