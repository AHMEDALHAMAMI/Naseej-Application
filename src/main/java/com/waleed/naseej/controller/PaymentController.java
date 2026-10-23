package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.InitiateMoyasarPaymentRequest;
import com.waleed.naseej.dto.in.MoyasarWebhookRequest;
import com.waleed.naseej.dto.in.PaymentRequest;
import com.waleed.naseej.dto.out.InitiateMoyasarPaymentResponse;
import com.waleed.naseej.dto.out.MoyasarPaymentStatusResponse;
import com.waleed.naseej.dto.out.MoyasarWebhookResponse;
import com.waleed.naseej.dto.out.PaymentResponse;
import com.waleed.naseej.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/get-all")
    public List<PaymentResponse> getAll() {
        return paymentService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request));
    }

    @PutMapping("/update/{id}")
    public PaymentResponse update(@PathVariable Integer id, @Valid @RequestBody PaymentRequest request) {
        return paymentService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        paymentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public PaymentResponse getById(@PathVariable Integer id) {
        return paymentService.getById(id);
    }

    @GetMapping("/get-status/{id}")
    public MoyasarPaymentStatusResponse getPaymentStatus(@PathVariable String id) {
        return paymentService.getPaymentStatus(id);
    }

    @PostMapping("/confirm/{id}")
    public PaymentResponse confirm(@PathVariable Integer id) {
        return paymentService.confirm(id);
    }

    @PostMapping("/refund/{id}")
    public PaymentResponse refund(@PathVariable Integer id) {
        return paymentService.refund(id);
    }

    @PostMapping("/webhook/moyasar")
    public MoyasarWebhookResponse moyasarWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecret,
            @Valid @RequestBody MoyasarWebhookRequest request) {
        return paymentService.handleMoyasarWebhook(webhookSecret, request);
    }

    @PostMapping("/initiate-moyasar")
    public ResponseEntity<InitiateMoyasarPaymentResponse> initiateMoyasar(
            @Valid @RequestBody InitiateMoyasarPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.initiateMoyasarPayment(request));
    }

    @PostMapping("/sync-moyasar/{id}")
    public MoyasarWebhookResponse syncMoyasar(@PathVariable Integer id) {
        return paymentService.syncMoyasarPayment(id);
    }

    @GetMapping("/moyasar-callback")
    public InitiateMoyasarPaymentResponse moyasarCallback(@RequestParam("id") String moyasarPaymentId) {
        MoyasarWebhookResponse synced = paymentService.syncMoyasarPaymentByMoyasarId(moyasarPaymentId);
        return paymentService.buildInitiateResponseFromWebhook(synced);
    }
}
