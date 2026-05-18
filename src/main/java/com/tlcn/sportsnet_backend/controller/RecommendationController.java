package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<?> getPersonalizedRecommendations(
            @RequestParam(defaultValue = "4") int top
    ) {
        return ResponseEntity.ok(recommendationService.getPersonalizedRecommendations(top));
    }
}
