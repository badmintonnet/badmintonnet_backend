package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.service.TournamentPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payment")
public class TournamentPaymentController {
    private final TournamentPaymentService paymentService;

    @PostMapping("/create")
    public ResponseEntity<?> createPayment(
            @RequestParam String categoryId,
            @RequestParam Double amount
    ) {
        return ResponseEntity.ok(paymentService.createPayment(categoryId, amount));
    }

    // Tạo thanh toán cho Club Tournament
    @PostMapping("/club/create")
    public ResponseEntity<?> createClubPayment(
            @RequestParam String participantId,
            @RequestParam(required = false) Double amount
    ) {
        return ResponseEntity.ok(paymentService.createClubPayment(participantId, amount));
    }

    @GetMapping("/vnpay-return")
    public ResponseEntity<?> vnPayReturn(@RequestParam Map<String,String> params) {
        return ResponseEntity.ok(paymentService.handleReturn(params));
    }
}
