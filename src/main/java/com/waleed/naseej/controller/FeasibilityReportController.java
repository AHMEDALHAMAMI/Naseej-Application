package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.FeasibilityReportRequest;
import com.waleed.naseej.dto.out.FeasibilityReportResponse;
import com.waleed.naseej.service.FeasibilityReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/feasibility-reports")
@RequiredArgsConstructor
public class FeasibilityReportController {

    private final FeasibilityReportService feasibilityReportService;

    @GetMapping("/get-all")
    public List<FeasibilityReportResponse> getAll() {
        return feasibilityReportService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<FeasibilityReportResponse> create(@Valid @RequestBody FeasibilityReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feasibilityReportService.create(request));
    }

    @PutMapping("/update/{id}")
    public FeasibilityReportResponse update(@PathVariable Integer id, @Valid @RequestBody FeasibilityReportRequest request) {
        return feasibilityReportService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        feasibilityReportService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public FeasibilityReportResponse getById(@PathVariable Integer id) {
        return feasibilityReportService.getById(id);
    }
}
