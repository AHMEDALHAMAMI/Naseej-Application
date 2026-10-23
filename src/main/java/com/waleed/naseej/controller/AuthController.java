package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.LoginRequest;
import com.waleed.naseej.dto.in.RefreshTokenRequest;
import com.waleed.naseej.dto.in.RegisterRequest;
import com.waleed.naseej.dto.in.ResetPasswordRequest;
import com.waleed.naseej.dto.in.SelectRoleRequest;
import com.waleed.naseej.dto.in.SendOtpRequest;
import com.waleed.naseej.dto.in.VerifyOtpRequest;
import com.waleed.naseej.dto.out.AuthResponse;
import com.waleed.naseej.dto.out.MessageResponse;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Google sign-in is not here: open /oauth2/authorization/google in the browser.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ----- Public -----

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-otp")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyOtp(request);
    }

    @PostMapping("/send-otp")
    public MessageResponse sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return authService.sendOtp(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    // ----- Logged in -----

    @PutMapping("/select-role")
    public AuthResponse selectRole(@AuthenticationPrincipal User user,
                                   @Valid @RequestBody SelectRoleRequest request) {
        return authService.selectRole(user, request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return authService.me(user);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@AuthenticationPrincipal User user) {
        return authService.logout(user);
    }
}