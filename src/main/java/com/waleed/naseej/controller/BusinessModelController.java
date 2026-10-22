package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.BusinessModelRequest;
import com.waleed.naseej.dto.in.ReviseByChatRequest;
import com.waleed.naseej.dto.out.BusinessModelResponse;
import com.waleed.naseej.service.BusinessModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/business-models")
@RequiredArgsConstructor
public class BusinessModelController {

    private final BusinessModelService businessModelService;

    @GetMapping("/get-all")
    public List<BusinessModelResponse> getAll() {
        return businessModelService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<BusinessModelResponse> create(@Valid @RequestBody BusinessModelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessModelService.create(request));
    }

    @PutMapping("/update/{id}")
    public BusinessModelResponse update(@PathVariable Integer id, @Valid @RequestBody BusinessModelRequest request) {
        return businessModelService.update(id, request);
    }

    @PutMapping("/revise-by-chat/{id}")
    public BusinessModelResponse reviseByChat(@PathVariable Integer id, @Valid @RequestBody ReviseByChatRequest request) {
        return businessModelService.reviseByChat(id, request.getMessage());
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        businessModelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public BusinessModelResponse getById(@PathVariable Integer id) {
        return businessModelService.getById(id);
    }
}
