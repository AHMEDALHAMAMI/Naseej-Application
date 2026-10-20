package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.FoundingMemberRequest;
import com.waleed.naseej.dto.out.FoundingMemberResponse;
import com.waleed.naseej.service.FoundingMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/founding-members")
@RequiredArgsConstructor
public class FoundingMemberController {

    private final FoundingMemberService foundingMemberService;

    @GetMapping("/get-all")
    public List<FoundingMemberResponse> getAll() {
        return foundingMemberService.getAll();
    }

    @PostMapping("/create")
    public ResponseEntity<FoundingMemberResponse> create(@Valid @RequestBody FoundingMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foundingMemberService.create(request));
    }

    @PutMapping("/update/{id}")
    public FoundingMemberResponse update(@PathVariable Integer id, @Valid @RequestBody FoundingMemberRequest request) {
        return foundingMemberService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        foundingMemberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/get/{id}")
    public FoundingMemberResponse getById(@PathVariable Integer id) {
        return foundingMemberService.getById(id);
    }
}
