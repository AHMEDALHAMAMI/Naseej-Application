package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.IdeaCheckRequest;
import com.waleed.naseej.dto.out.IdeaCheckResponse;
import com.waleed.naseej.service.IdeaCheckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/idea-checks")
@RequiredArgsConstructor
public class IdeaCheckController {

    private final IdeaCheckService ideaCheckService;

    @GetMapping("/get-all")
    public List<IdeaCheckResponse> getAll() {
        return ideaCheckService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<IdeaCheckResponse> create(@Valid @RequestBody IdeaCheckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ideaCheckService.create(request));
    }

    @PutMapping("/update/{id}")
    public IdeaCheckResponse update(@PathVariable Integer id, @Valid @RequestBody IdeaCheckRequest request) {
        return ideaCheckService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        ideaCheckService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public IdeaCheckResponse getById(@PathVariable Integer id) {
        return ideaCheckService.getById(id);
    }
}
