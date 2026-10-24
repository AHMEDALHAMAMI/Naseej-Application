package com.waleed.naseej.controller;

import com.waleed.naseej.dto.out.AdminStatsResponse;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.service.AdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Admin dashboard: numbers and investor verification (ADMIN only, see SecurityConfig). */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

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
}
