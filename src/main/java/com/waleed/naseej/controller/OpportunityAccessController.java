package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.OpportunityAccessRequest;
import com.waleed.naseej.dto.out.OpportunityAccessResponse;
import com.waleed.naseej.service.OpportunityAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/opportunity-access")
@RequiredArgsConstructor
public class OpportunityAccessController {

    private final OpportunityAccessService opportunityAccessService;

    @GetMapping("/get-all")
    public List<OpportunityAccessResponse> getAll() {
        return opportunityAccessService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<OpportunityAccessResponse> create(@Valid @RequestBody OpportunityAccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(opportunityAccessService.create(request));
    }

    @PutMapping("/update/{id}")
    public OpportunityAccessResponse update(@PathVariable Integer id, @Valid @RequestBody OpportunityAccessRequest request) {
        return opportunityAccessService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        opportunityAccessService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public OpportunityAccessResponse getById(@PathVariable Integer id) {
        return opportunityAccessService.getById(id);
    }
}
