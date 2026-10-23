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

    @PostMapping("/unlock")
    public ResponseEntity<OpportunityAccessResponse> unlock(@Valid @RequestBody OpportunityAccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(opportunityAccessService.unlock(request));
    }

    @GetMapping("/check")
    public boolean check(@RequestParam Integer investorId, @RequestParam Integer ideaId) {
        return opportunityAccessService.check(investorId, ideaId);
    }

    @GetMapping("/get-by-investor/{investorId}")
    public List<OpportunityAccessResponse> getByInvestor(@PathVariable Integer investorId) {
        return opportunityAccessService.getByInvestor(investorId);
    }

    @GetMapping("/count-by-idea/{ideaId}")
    public long countByIdea(@PathVariable Integer ideaId) {
        return opportunityAccessService.countByIdea(ideaId);
    }
}
