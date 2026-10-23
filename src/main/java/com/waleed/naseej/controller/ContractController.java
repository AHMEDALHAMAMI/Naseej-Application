package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.ContractRequest;
import com.waleed.naseej.dto.in.ContractSignatureRequest;
import com.waleed.naseej.dto.in.DraftContractRequest;
import com.waleed.naseej.dto.in.ProjectUpdateRequest;
import com.waleed.naseej.dto.out.ContractResponse;
import com.waleed.naseej.dto.out.ProjectUpdateResponse;
import com.waleed.naseej.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping("/get-all")
    public List<ContractResponse> getAll() {
        return contractService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.create(request));
    }

    @PutMapping("/update/{id}")
    public ContractResponse update(@PathVariable Integer id, @Valid @RequestBody ContractRequest request) {
        return contractService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        contractService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public ContractResponse getById(@PathVariable Integer id) {
        return contractService.getById(id);
    }

    @PostMapping("/draft")
    public ResponseEntity<ContractResponse> draft(@Valid @RequestBody DraftContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.draftContract(request));
    }

    @PostMapping("/sign/{id}")
    public ContractResponse sign(@PathVariable Integer id, @Valid @RequestBody ContractSignatureRequest request) {
        return contractService.signContract(id, request);
    }

    @PostMapping("/terminate/{id}")
    public ContractResponse terminate(@PathVariable Integer id) {
        return contractService.terminateContract(id);
    }

    @PostMapping("/add-update/{id}")
    public ResponseEntity<ProjectUpdateResponse> addUpdate(
            @PathVariable Integer id,
            @Valid @RequestBody ProjectUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                contractService.addProjectUpdate(id, request.getStage(), request.getNote()));
    }
}
