package com.waleed.naseej.controller;

import com.waleed.naseej.dto.out.AdminStatsResponse;
import com.waleed.naseej.dto.out.ExpiredContractsResponse;
import com.waleed.naseej.dto.out.IdeaResponse;
import com.waleed.naseej.dto.out.RefundResultResponse;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.AdminService;
import com.waleed.naseej.service.ContractFlowService;
import com.waleed.naseej.service.RefundService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Admin dashboard: numbers, investor verification, moderation, refunds and the 90-day check (ADMIN only). */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final RefundService refundService;
    private final ContractFlowService contractFlowService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/investors")
    public List<UserResponse> investors(@RequestParam(required = false, defaultValue = "PENDING") String status) {
        return adminService.investors(status);
    }

    @PutMapping("/investors/verify/{id}")
    public UserResponse verify(@PathVariable Integer id) {
        return adminService.verifyInvestor(id);
    }

    @PutMapping("/investors/reject/{id}")
    public UserResponse reject(@PathVariable Integer id) {
        return adminService.rejectInvestor(id);
    }

    // ----- Moderation -----

    // ?disabled=false re-enables the account
    @PutMapping("/users/{id}/disable")
    public UserResponse disableUser(@AuthenticationPrincipal User admin,
                                    @PathVariable Integer id,
                                    @RequestParam(defaultValue = "true") boolean disabled) {
        return adminService.setUserDisabled(admin, id, disabled);
    }

    // ?hidden=false puts the idea back with the status it had
    @PutMapping("/ideas/{id}/hide")
    public IdeaResponse hideIdea(@PathVariable Integer id, @RequestParam(defaultValue = "true") boolean hidden) {
        return adminService.setIdeaHidden(id, hidden);
    }

    // ----- Money and contracts -----

    @PostMapping("/payments/{id}/refund")
    public RefundResultResponse refund(@PathVariable Integer id) {
        return refundService.refund(id);
    }

    // Also runs every night on its own; n8n or the admin can trigger it any time
    @PostMapping("/contracts/expire-overdue")
    public ExpiredContractsResponse expireOverdue() {
        return contractFlowService.expireOverdue();
    }
}
