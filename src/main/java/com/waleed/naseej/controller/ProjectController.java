package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.ProjectStageRequest;
import com.waleed.naseej.dto.in.SignContractRequest;
import com.waleed.naseej.dto.out.MessageResponse;
import com.waleed.naseej.dto.out.ProjectResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.ContractFlowService;
import com.waleed.naseej.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** "My projects" for investors and founders, and the contract steps after adoption. */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ContractFlowService contractFlowService;

    @GetMapping("/mine")
    public List<ProjectResponse> mine(@AuthenticationPrincipal User user) {
        return projectService.mine(user);
    }

    @GetMapping("/stages")
    public List<String> stages() {
        return ProjectService.STAGES;
    }

    @PostMapping("/stage/{contractId}")
    public ProjectResponse addStage(@AuthenticationPrincipal User user,
                                    @PathVariable Integer contractId,
                                    @Valid @RequestBody ProjectStageRequest request) {
        return projectService.addStage(user, contractId, request);
    }

    // ----- Contract: purchase → investor signs → ACTIVE (→ one extension). The idea owner doesn't sign -----

    @PostMapping("/{contractId}/sign-otp")
    public MessageResponse sendSignOtp(@AuthenticationPrincipal User user, @PathVariable Integer contractId) {
        return contractFlowService.sendSignOtp(user, contractId);
    }

    @PostMapping("/{contractId}/sign")
    public ProjectResponse sign(@AuthenticationPrincipal User user,
                                @PathVariable Integer contractId,
                                @Valid @RequestBody SignContractRequest request,
                                HttpServletRequest http) {
        return contractFlowService.sign(user, contractId, request, http.getRemoteAddr());
    }

    // The one 60-day extension of the opening deadline (investor, near the deadline, with recent progress)
    @PostMapping("/{contractId}/extend")
    public ProjectResponse extend(@AuthenticationPrincipal User user, @PathVariable Integer contractId) {
        return contractFlowService.extend(user, contractId);
    }

    // Print-ready contract page (Arabic); "Save as PDF" in the browser gives the PDF
    @GetMapping("/{contractId}/contract")
    public ResponseEntity<String> contract(@AuthenticationPrincipal User user, @PathVariable Integer contractId) {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(contractFlowService.contractDocument(user, contractId));
    }
}
