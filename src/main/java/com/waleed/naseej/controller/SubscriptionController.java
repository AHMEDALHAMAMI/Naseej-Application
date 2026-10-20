package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.SubscriptionRequest;
import com.waleed.naseej.dto.out.SubscriptionResponse;
import com.waleed.naseej.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/get-all")
    public List<SubscriptionResponse> getAll() {
        return subscriptionService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody SubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.create(request));
    }

    @PutMapping("/update/{id}")
    public SubscriptionResponse update(@PathVariable Integer id, @Valid @RequestBody SubscriptionRequest request) {
        return subscriptionService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        subscriptionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public SubscriptionResponse getById(@PathVariable Integer id) {
        return subscriptionService.getById(id);
    }
}
