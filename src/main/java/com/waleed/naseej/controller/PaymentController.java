package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.PaymentRequest;
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
}
