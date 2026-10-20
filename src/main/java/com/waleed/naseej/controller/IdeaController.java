package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.IdeaRequest;
import com.waleed.naseej.dto.out.IdeaResponse;
import com.waleed.naseej.service.IdeaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/ideas")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaService ideaService;

    @GetMapping("/get-all")
    public List<IdeaResponse> getAll() {
        return ideaService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<IdeaResponse> create(@Valid @RequestBody IdeaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ideaService.create(request));
    }

    @PutMapping("/update/{id}")
    public IdeaResponse update(@PathVariable Integer id, @Valid @RequestBody IdeaRequest request) {
        return ideaService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        ideaService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public IdeaResponse getById(@PathVariable Integer id) {
        return ideaService.getById(id);
    }
}
