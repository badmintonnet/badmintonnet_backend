package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.club_tournament.ClubRepresentativeRequest;
import com.tlcn.sportsnet_backend.dto.club_tournament.ClubLineupRequest;
import com.tlcn.sportsnet_backend.dto.club_tournament.ClubTournamentRegistrationRequest;
import com.tlcn.sportsnet_backend.dto.club_tournament.UpdateRosterRequest;
import com.tlcn.sportsnet_backend.enums.ClubTournamentParticipantStatusEnum;
import com.tlcn.sportsnet_backend.service.ClubTournamentResultService;
import com.tlcn.sportsnet_backend.service.ClubTournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/club-tournament")
@RequiredArgsConstructor
public class ClubTournamentController {

    private final ClubTournamentService clubTournamentService;
    private final ClubTournamentResultService clubTournamentResultService;

    /**
     * CLB chủ đăng ký tham gia tournament
     * POST /api/club-tournament/{tournamentId}/register
     * Body: { clubId, rosterAccountIds: [...] }
     */
    @PostMapping("/{tournamentId}/register")
    public ResponseEntity<?> registerClub(
            @PathVariable String tournamentId,
            @RequestBody ClubTournamentRegistrationRequest request) {
        return ResponseEntity.ok(clubTournamentService.registerClub(tournamentId, request));
    }

    /**
     * CLB chủ cập nhật roster trước deadline
     * PUT /api/club-tournament/participant/{participantId}/roster
     * Body: { rosterAccountIds: [...] }
     */
    @PutMapping("/participant/{participantId}/roster")
    public ResponseEntity<?> updateRoster(
            @PathVariable String participantId,
            @RequestBody UpdateRosterRequest request) {
        return ResponseEntity.ok(clubTournamentService.updateRoster(participantId, request));
    }

    /**
     * CLB chủ hủy đăng ký
     * DELETE /api/club-tournament/participant/{participantId}
     */
    @DeleteMapping("/participant/{participantId}")
    public ResponseEntity<?> cancelRegistration(@PathVariable String participantId) {
        clubTournamentService.cancelRegistration(participantId);
        return ResponseEntity.ok("Đã hủy đăng ký thành công");
    }

    /**
     * Lấy danh sách tất cả CLB đã đăng ký trong một tournament (admin & public)
     * GET /api/club-tournament/{tournamentId}/participants
     */
    @GetMapping("/{tournamentId}/participants")
    public ResponseEntity<?> getAllClubParticipants(
            @PathVariable String tournamentId,
            @RequestParam(required = false) List<ClubTournamentParticipantStatusEnum> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(clubTournamentService.getAllClubParticipants(tournamentId, status, page, size));
    }

    /**
     * Xem chi tiết đăng ký của một CLB (bao gồm full roster)
     * GET /api/club-tournament/participant/{participantId}
     */
    @GetMapping("/participant/{participantId}")
    public ResponseEntity<?> getClubParticipantDetail(@PathVariable String participantId) {
        return ResponseEntity.ok(clubTournamentService.getClubParticipantDetail(participantId));
    }

    /**
     * Kiểm tra CLB của người dùng hiện tại đã đăng ký tournament chưa
     * GET /api/club-tournament/{tournamentId}/my-participation?clubId=xxx
     */
    @GetMapping("/{tournamentId}/my-participation")
    public ResponseEntity<?> getMyClubParticipation(
            @PathVariable String tournamentId,
            @RequestParam String clubId) {
        return ResponseEntity.ok(clubTournamentService.getMyClubParticipation(tournamentId, clubId));
    }

    /**
     * Lấy danh sách tournament của CLB (My Club - Tab Giải đấu)
     * GET /api/club-tournament/my-tournaments?clubId=xxx
     */
    @GetMapping("/my-tournaments")
    public ResponseEntity<?> getMyClubTournaments(@RequestParam String clubId) {
        return ResponseEntity.ok(clubTournamentService.getMyClubTournaments(clubId));
    }

    /**
     * Admin duyệt đăng ký CLB
     * PUT /api/club-tournament/participant/{participantId}/approve
     */
    @PutMapping("/participant/{participantId}/approve")
    public ResponseEntity<?> approveClubParticipant(@PathVariable String participantId) {
        clubTournamentService.approveClubParticipant(participantId);
        return ResponseEntity.ok("CLB đã được duyệt tham gia tournament");
    }

    /**
     * Admin từ chối đăng ký CLB
     * PUT /api/club-tournament/participant/{participantId}/reject
     */
    @PutMapping("/participant/{participantId}/reject")
    public ResponseEntity<?> rejectClubParticipant(@PathVariable String participantId) {
        clubTournamentService.rejectClubParticipant(participantId);
        return ResponseEntity.ok("CLB đã bị từ chối tham gia tournament");
    }

    // =========================================================
    // ĐẠI DIỆN & BẢNG ĐẤU
    // =========================================================

    /**
     * Owner CLB đặt / cập nhật lineup (partial merge): body { "lineup": { "SINGLES_1": "...", ... } }
     * PUT /api/club-tournament/participants/{participantId}/lineup
     */
    @PutMapping("/participants/{participantId}/lineup")
    public ResponseEntity<?> setLineup(
            @PathVariable String participantId,
            @RequestBody ClubLineupRequest request) {
        return ResponseEntity.ok(clubTournamentService.setLineup(participantId, request));
    }

    /**
     * Owner CLB xem format + các slot và trạng thái đã chọn.
     * GET /api/club-tournament/participants/{participantId}/lineup
     */
    @GetMapping("/participants/{participantId}/lineup")
    public ResponseEntity<?> getLineup(@PathVariable String participantId) {
        return ResponseEntity.ok(clubTournamentService.getLineup(participantId));
    }

    /**
     * Owner CLB chọn đại diện đơn nam
     * PUT /api/club-tournament/participants/{participantId}/set-representative
     */
    @PutMapping("/participants/{participantId}/set-representative")
    public ResponseEntity<?> setRepresentative(
            @PathVariable String participantId,
            @RequestBody ClubRepresentativeRequest request) {
        clubTournamentService.setRepresentative(participantId, request.getRosterEntryId());
        return ResponseEntity.ok("Đã chọn đại diện đơn nam");
    }

    /**
     * Owner xem đại diện hiện tại
     * GET /api/club-tournament/participants/{participantId}/representative
     */
    @GetMapping("/participants/{participantId}/representative")
    public ResponseEntity<?> getRepresentative(@PathVariable String participantId) {
        return ResponseEntity.ok(clubTournamentService.getRepresentative(participantId));
    }

    /**
     * Lấy bảng đấu CLB (đã tạo)
     * GET /api/club-tournament/tournament/{tournamentId}/bracket
     */
    @GetMapping("/tournament/{tournamentId}/bracket")
    public ResponseEntity<?> getClubBracket(@PathVariable String tournamentId) {
        return ResponseEntity.ok(clubTournamentService.getClubBracketByTournament(tournamentId));
    }

    /**
     * Lấy kết quả CLUB tournament (podium, ranking, stats, key matches)
     * GET /api/club-tournament/tournament/{tournamentId}/results
     */
    @GetMapping("/tournament/{tournamentId}/results")
    public ResponseEntity<?> getClubTournamentResults(@PathVariable String tournamentId) {
        return ResponseEntity.ok(clubTournamentResultService.getResults(tournamentId));
    }
}
