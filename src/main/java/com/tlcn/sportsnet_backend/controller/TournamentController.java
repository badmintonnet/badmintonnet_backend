package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import com.tlcn.sportsnet_backend.repository.TournamentRepository;
import com.tlcn.sportsnet_backend.service.TournamentCategoryService;
import com.tlcn.sportsnet_backend.service.TournamentParticipantService;
import com.tlcn.sportsnet_backend.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tournaments")
public class TournamentController {
    private final TournamentService tournamentService;
    private final TournamentCategoryService tournamentCategoryService;
    private final TournamentParticipantService participantService;

    @GetMapping
    public ResponseEntity<?> getAllTournaments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) TournamentParticipationTypeEnum participationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate organizationDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate organizationDateTo) {
        return ResponseEntity.ok(tournamentService.getAllTournament(page, size, participationType, content, organizationDateFrom, organizationDateTo));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getTournamentBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(tournamentService.getBySlug(slug));
    }

    @GetMapping("/nearby")
    public ResponseEntity<?> getNearestTournaments(
            @RequestParam(defaultValue = "5") int top) {
        return ResponseEntity.ok(tournamentService.getNearestTournaments(top));
    }

    @PostMapping("/{categoryId}/register/single")
    public ResponseEntity<?> joinSingleTournament(@PathVariable String categoryId){
        return ResponseEntity.ok(participantService.joinSingle(categoryId));
    }

    @PostMapping("/{categoryId}/register/double")
    public ResponseEntity<?> joinDoubleTournament(@PathVariable String categoryId){
        return ResponseEntity.ok(participantService.joinDouble(categoryId));
    }
    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<?> getDetailCategoryById(@PathVariable String categoryId) {
        return ResponseEntity.ok(tournamentCategoryService.getDetailCategoryById(categoryId));
    }

    @GetMapping("/get-all-partner/{categoryId}")
    public ResponseEntity<?> getAllPartner(@PathVariable String categoryId) {
        return ResponseEntity.ok(tournamentService.getAllPartner(categoryId));
    }
}
