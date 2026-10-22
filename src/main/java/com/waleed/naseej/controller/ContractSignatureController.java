package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.ContractSignatureRequest;
import com.waleed.naseej.dto.out.ContractSignatureResponse;
import com.waleed.naseej.dto.out.ContractSigningCheckResponse;
import com.waleed.naseej.service.ContractSignatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/contract-signatures")
@RequiredArgsConstructor
public class ContractSignatureController {

    private final ContractSignatureService contractSignatureService;

    @GetMapping("/get-all")
    public List<ContractSignatureResponse> getAll() {
        return contractSignatureService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<ContractSignatureResponse> create(@Valid @RequestBody ContractSignatureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractSignatureService.create(request));
    }

    @PutMapping("/update/{id}")
    public ContractSignatureResponse update(@PathVariable Integer id, @Valid @RequestBody ContractSignatureRequest request) {
        return contractSignatureService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        contractSignatureService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public ContractSignatureResponse getById(@PathVariable Integer id) {
        return contractSignatureService.getById(id);
    }

    @PostMapping("/sign/{contractId}")
    public ResponseEntity<ContractSignatureResponse> sign(
            @PathVariable Integer contractId,
            @Valid @RequestBody ContractSignatureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractSignatureService.signForContract(contractId, request));
    }

    @GetMapping("/check/contract/{contractId}")
    public ContractSigningCheckResponse checkSigningStatus(@PathVariable Integer contractId) {
        return contractSignatureService.checkSigningStatus(contractId);
    }
}
