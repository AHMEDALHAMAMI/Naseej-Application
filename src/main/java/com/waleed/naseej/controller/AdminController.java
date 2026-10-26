package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.AdminPasswordRequest;
import com.waleed.naseej.dto.in.AdminUserRequest;
import com.waleed.naseej.dto.in.AdminUserUpdateRequest;
import com.waleed.naseej.dto.out.AdminStatsResponse;
import com.waleed.naseej.dto.out.AdminUserResponse;
import com.waleed.naseej.dto.out.ExpiredContractsResponse;
import com.waleed.naseej.dto.out.IdeaResponse;
import com.waleed.naseej.dto.out.RefundResultResponse;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.AdminService;
import com.waleed.naseej.service.AdminUserService;
import com.waleed.naseej.service.ContractFlowService;
import com.waleed.naseej.service.RefundService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Admin dashboard: numbers, member management, investor verification, moderation, refunds and the contract deadline check (ADMIN only). */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final RefundService refundService;
    private final ContractFlowService contractFlowService;
    private final AdminUserService adminUserService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }

    // ---------- Members ----------

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminUserService.users();
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(@AuthenticationPrincipal User admin,
                                                        @Valid @RequestBody AdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(admin, request));
    }

    @PutMapping("/users/{id}")
    public AdminUserResponse updateUser(@AuthenticationPrincipal User admin, @PathVariable Integer id,
                                        @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.update(admin, id, request);
    }

    @PutMapping("/users/{id}/suspend")
    public AdminUserResponse suspend(@AuthenticationPrincipal User admin, @PathVariable Integer id) {
        return adminUserService.suspend(admin, id);
    }

    @PutMapping("/users/{id}/reactivate")
    public AdminUserResponse reactivate(@AuthenticationPrincipal User admin, @PathVariable Integer id) {
        return adminUserService.reactivate(admin, id);
    }

    @PutMapping("/users/{id}/password")
    public AdminUserResponse setPassword(@AuthenticationPrincipal User admin, @PathVariable Integer id,
                                         @Valid @RequestBody AdminPasswordRequest request) {
        return adminUserService.setPassword(admin, id, request);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal User admin, @PathVariable Integer id) {
        adminUserService.delete(admin, id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Investor verification ----------

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
