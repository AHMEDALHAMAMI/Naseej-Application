package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.ProjectUpdateRequest;
import com.waleed.naseej.dto.out.ProjectUpdateResponse;
import com.waleed.naseej.service.ProjectUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/project-updates")
@RequiredArgsConstructor
public class ProjectUpdateController {

    private final ProjectUpdateService projectUpdateService;

    @GetMapping("/get-all")
    public List<ProjectUpdateResponse> getAll() {
        return projectUpdateService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<ProjectUpdateResponse> create(@Valid @RequestBody ProjectUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectUpdateService.create(request));
    }

    @PutMapping("/update/{id}")
    public ProjectUpdateResponse update(@PathVariable Integer id, @Valid @RequestBody ProjectUpdateRequest request) {
        return projectUpdateService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        projectUpdateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public ProjectUpdateResponse getById(@PathVariable Integer id) {
        return projectUpdateService.getById(id);
    }
}
