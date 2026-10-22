package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.NearbyPlaceRequest;
import com.waleed.naseej.dto.out.NearbyPlaceResponse;
import com.waleed.naseej.service.NearbyPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/nearby-places")
@RequiredArgsConstructor
public class NearbyPlaceController {

    private final NearbyPlaceService nearbyPlaceService;

    @GetMapping("/get-all")
    public List<NearbyPlaceResponse> getAll() {
        return nearbyPlaceService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<NearbyPlaceResponse> create(@Valid @RequestBody NearbyPlaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nearbyPlaceService.create(request));
    }

    @PutMapping("/update/{id}")
    public NearbyPlaceResponse update(@PathVariable Integer id, @Valid @RequestBody NearbyPlaceRequest request) {
        return nearbyPlaceService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        nearbyPlaceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public NearbyPlaceResponse getById(@PathVariable Integer id) {
        return nearbyPlaceService.getById(id);
    }

    @GetMapping("/get-by-idea/{ideaId}")
    public List<NearbyPlaceResponse> getByIdea(@PathVariable Integer ideaId) {
        return nearbyPlaceService.getByIdea(ideaId);
    }

    @GetMapping("/count-by-idea/{ideaId}")
    public long countByIdea(@PathVariable Integer ideaId,
                            @RequestParam(defaultValue = "2") Double radiusKm) {
        return nearbyPlaceService.countByIdea(ideaId, radiusKm);
    }

    @PostMapping("/refresh/{ideaId}")
    public List<NearbyPlaceResponse> refresh(@PathVariable Integer ideaId) {
        return nearbyPlaceService.refresh(ideaId);
    }
}
