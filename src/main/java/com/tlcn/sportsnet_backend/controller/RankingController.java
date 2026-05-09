package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rankings")
public class RankingController {
    private final RankingService rankingService;

    @GetMapping
    public ResponseEntity<?> getRankings(
            @RequestParam(defaultValue = "GLOBAL") String scope,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) String club,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(rankingService.getRankings(scope, area, province, ward, club, page, size));
    }
}
