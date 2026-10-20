package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.NeighborhoodRequest;
import com.waleed.naseej.dto.out.NeighborhoodResponse;
import com.waleed.naseej.service.NeighborhoodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/neighborhoods")
@RequiredArgsConstructor
public class NeighborhoodController {

    private final NeighborhoodService neighborhoodService;

    @GetMapping("/get-all")
    public List<NeighborhoodResponse> getAll() {
        return neighborhoodService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<NeighborhoodResponse> create(@Valid @RequestBody NeighborhoodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(neighborhoodService.create(request));
    }

    @PutMapping("/update/{id}")
    public NeighborhoodResponse update(@PathVariable Integer id, @Valid @RequestBody NeighborhoodRequest request) {
        return neighborhoodService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        neighborhoodService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public NeighborhoodResponse getById(@PathVariable Integer id) {
        return neighborhoodService.getById(id);
    }
}
