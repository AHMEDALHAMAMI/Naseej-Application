package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.ConfirmUnlockPaymentRequest;
import com.waleed.naseej.dto.in.UnlockPartnershipRequest;
import com.waleed.naseej.dto.in.UnlockPaymentRequest;
import com.waleed.naseej.dto.out.InitiateMoyasarPaymentResponse;
import com.waleed.naseej.dto.out.UnlockQuoteResponse;
import com.waleed.naseej.dto.out.UnlockResultResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.UnlockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Investor: AI price, pay upfront (Moyasar), success partnership, or adopt an own idea. */
@RestController
@RequestMapping("/unlock")
@RequiredArgsConstructor
public class UnlockController {

    private final UnlockService unlockService;

    @GetMapping("/quote/{ideaId}")
    public UnlockQuoteResponse quote(@AuthenticationPrincipal User user, @PathVariable Integer ideaId) {
        return unlockService.quote(user, ideaId);
    }

    @PostMapping("/pay/{ideaId}")
    public ResponseEntity<InitiateMoyasarPaymentResponse> pay(@AuthenticationPrincipal User user,
                                                              @PathVariable Integer ideaId,
                                                              @RequestBody(required = false) UnlockPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unlockService.startPayment(user, ideaId, request));
    }

    @PostMapping("/confirm-payment/{ideaId}")
    public UnlockResultResponse confirmPayment(@AuthenticationPrincipal User user,
                                               @PathVariable Integer ideaId,
                                               @Valid @RequestBody ConfirmUnlockPaymentRequest request) {
        return unlockService.confirmPayment(user, ideaId, request);
    }

    @PostMapping("/partnership/{ideaId}")
    public UnlockResultResponse partnership(@AuthenticationPrincipal User user,
                                            @PathVariable Integer ideaId,
                                            @Valid @RequestBody UnlockPartnershipRequest request,
                                            HttpServletRequest http) {
        return unlockService.partnership(user, ideaId, request, http.getRemoteAddr());
    }

    @PostMapping("/adopt/{ideaId}")
    public UnlockResultResponse adopt(@AuthenticationPrincipal User user, @PathVariable Integer ideaId) {
        return unlockService.adoptOwnIdea(user, ideaId);
    }
}
