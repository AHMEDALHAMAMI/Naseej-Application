package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.PartnershipAgreementRequest;
import com.waleed.naseej.dto.out.PartnershipAgreementResponse;
import com.waleed.naseej.service.PartnershipAgreementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/partnership-agreements")
@RequiredArgsConstructor
public class PartnershipAgreementController {

    private final PartnershipAgreementService partnershipAgreementService;

    @GetMapping("/get-all")
    public List<PartnershipAgreementResponse> getAll() {
        return partnershipAgreementService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<PartnershipAgreementResponse> create(@Valid @RequestBody PartnershipAgreementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partnershipAgreementService.create(request));
    }

    @PutMapping("/update/{id}")
    public PartnershipAgreementResponse update(@PathVariable Integer id, @Valid @RequestBody PartnershipAgreementRequest request) {
        return partnershipAgreementService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        partnershipAgreementService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public PartnershipAgreementResponse getById(@PathVariable Integer id) {
        return partnershipAgreementService.getById(id);
    }
}
