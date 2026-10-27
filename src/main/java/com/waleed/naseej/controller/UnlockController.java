package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.CheckoutRequest;
import com.waleed.naseej.dto.in.ConfirmUnlockPaymentRequest;
import com.waleed.naseej.dto.in.OwnerLaunchRequest;
import com.waleed.naseej.dto.in.UnlockPartnershipRequest;
import com.waleed.naseej.dto.in.UnlockPaymentRequest;
import com.waleed.naseej.dto.out.AgreementResponse;
import com.waleed.naseej.dto.out.CheckoutResponse;
import com.waleed.naseej.dto.out.InitiateMoyasarPaymentResponse;
import com.waleed.naseej.dto.out.OwnerLaunchTermsResponse;
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

    /** Opens Moyasar's payment form: amount, publishable key and the metadata that ties it to this checkout. */
    @PostMapping("/checkout/{ideaId}")
    public ResponseEntity<CheckoutResponse> checkout(@AuthenticationPrincipal User user,
                                                     @PathVariable Integer ideaId,
                                                     @Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unlockService.checkout(user, ideaId, request));
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

    /** The investor's own idea, adopted with no fee (sign the contract in My projects). */
    @PostMapping("/adopt/{ideaId}")
    public UnlockResultResponse adopt(@AuthenticationPrincipal User user, @PathVariable Integer ideaId) {
        return unlockService.adoptOwnIdea(user, ideaId);
    }

    /** The owner's own idea: the service agreement they would sign and whether they can launch now. */
    @GetMapping("/owner-terms/{ideaId}")
    public OwnerLaunchTermsResponse ownerTerms(@AuthenticationPrincipal User user, @PathVariable Integer ideaId) {
        return unlockService.ownerTerms(user, ideaId);
    }

    /** The owner signs the service agreement; the response opens Moyasar's form for the service fee. */
    @PostMapping("/owner-checkout/{ideaId}")
    public ResponseEntity<CheckoutResponse> ownerCheckout(@AuthenticationPrincipal User user,
                                                          @PathVariable Integer ideaId,
                                                          @Valid @RequestBody OwnerLaunchRequest request,
                                                          HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(unlockService.ownerCheckout(user, ideaId, request, http.getRemoteAddr()));
    }

    /** After Moyasar: the fee is checked with Moyasar and the project starts. */
    @PostMapping("/owner-confirm/{ideaId}")
    public UnlockResultResponse ownerConfirm(@AuthenticationPrincipal User user,
                                             @PathVariable Integer ideaId,
                                             @Valid @RequestBody ConfirmUnlockPaymentRequest request) {
        return unlockService.confirmOwnerLaunch(user, ideaId, request);
    }

    /** A readable copy of a project's agreement, for its investor and founder. */
    @GetMapping("/agreement/{contractId}")
    public AgreementResponse agreement(@AuthenticationPrincipal User user, @PathVariable Integer contractId) {
        return unlockService.agreement(user, contractId);
    }
}
