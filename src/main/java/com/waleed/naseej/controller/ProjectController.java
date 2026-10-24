package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.ProjectStageRequest;
import com.waleed.naseej.dto.out.ProjectResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** "My projects" for investors and founders. */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

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
}
