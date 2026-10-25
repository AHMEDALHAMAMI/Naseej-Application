package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.LoginRequest;
import com.waleed.naseej.dto.in.RefreshTokenRequest;
import com.waleed.naseej.dto.in.RegisterRequest;
import com.waleed.naseej.dto.in.ResetPasswordRequest;
import com.waleed.naseej.dto.in.SelectRoleRequest;
import com.waleed.naseej.dto.in.SendOtpRequest;
import com.waleed.naseej.dto.in.UpdateLocationRequest;
import com.waleed.naseej.dto.in.UpdateProfileRequest;
import com.waleed.naseej.dto.in.VerifyOtpRequest;
import com.waleed.naseej.dto.out.AuthResponse;
import com.waleed.naseej.dto.out.MessageResponse;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.OtpPurpose;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.exception.UnauthorizedException;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Sign-up, email OTP, login, refresh/logout, Google sign-in and role selection.
 * No @Transactional on purpose: a wrong OTP attempt must stay saved even though an exception is thrown.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_OTP_MESSAGE = "If this email is registered, a verification code has been sent";

    private final UserRepository userRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final UserService userService;
    private final LocationService locationService;

    @Value("${jwt.refresh-token-days}")
    private long refreshTokenDays;

    // ---------- Email sign-up ----------

    public MessageResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        Integer neighborhoodId = resolveNeighborhoodId(
                request.getRole(), request.getNeighborhoodId(), request.getLatitude(), request.getLongitude());
        validateSignUpRole(request.getRole(), neighborhoodId);
        validateInvestorProfile(request.getRole(), request.getCrNumber(),
                request.getInvestmentBudget(), request.getInvestmentFields());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Phone is already registered");
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .authProvider(AuthProvider.LOCAL)
                .role(request.getRole())
                .neighborhood(findNeighborhood(neighborhoodId))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .whatsappOptIn(Boolean.TRUE.equals(request.getWhatsappOptIn()))
                .emailVerified(false)
                .enabled(false)
                .build();
        applyInvestorProfile(user, request.getRole(), request.getCrNumber(),
                request.getInvestmentBudget(), request.getInvestmentFields());
        userRepository.save(user);

        otpService.sendOtp(user, OtpPurpose.REGISTER);
        return new MessageResponse("Account created. Enter the 6-digit code we sent to " + email);
    }

    // ---------- OTP ----------

    /** REGISTER: verifies the email and logs in. LOGIN: passwordless login with the email code. */
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        if (request.getPurpose() != OtpPurpose.REGISTER && request.getPurpose() != OtpPurpose.LOGIN) {
            throw new BadRequestException("Purpose must be REGISTER or LOGIN");
        }
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadRequestException("Invalid email or code"));

        otpService.verifyOtp(user, request.getPurpose(), request.getCode());

        if (request.getPurpose() == OtpPurpose.REGISTER) {
            user.setEmailVerified(true);
            user.setEnabled(true);
            userRepository.save(user);
        } else {
            ensureCanLogin(user);
        }
        return issueTokens(user);
    }

    /** Resend the sign-up code, request a login code, or request a password reset code. */
    public MessageResponse sendOtp(SendOtpRequest request) {
        OtpPurpose purpose = request.getPurpose();
        if (purpose == OtpPurpose.CONTRACT_SIGN) {
            throw new BadRequestException("Contract codes are sent from the contract signing page");
        }

        // Same answer whether the email exists or not, so nobody can check which emails are registered
        userRepository.findByEmail(normalizeEmail(request.getEmail())).ifPresent(user -> {
            if (purpose == OtpPurpose.REGISTER && Boolean.TRUE.equals(user.getEmailVerified())) {
                return; // already verified, nothing to resend
            }
            if (purpose == OtpPurpose.RESET_PASSWORD && user.getAuthProvider() == AuthProvider.GOOGLE
                    && user.getPassword() == null) {
                return; // Google-only account has no password to reset
            }
            otpService.sendOtp(user, purpose);
        });
        return new MessageResponse(GENERIC_OTP_MESSAGE);
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadRequestException("Invalid email or code"));

        otpService.verifyOtp(user, OtpPurpose.RESET_PASSWORD, request.getCode());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // The code proves the user owns the email
        user.setEmailVerified(true);
        // Log out every device
        user.setRefreshTokenHash(null);
        user.setRefreshTokenExpiresAt(null);
        userRepository.save(user);
        return new MessageResponse("Password changed, you can log in now");
    }

    // ---------- Login / tokens ----------

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.getPassword() == null) {
            throw new BadRequestException("This account uses Google sign-in");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            otpService.sendOtp(user, OtpPurpose.REGISTER);
            throw new ForbiddenException("Email not verified. We sent a new code to " + user.getEmail());
        }
        ensureCanLogin(user);
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        User user = userRepository.findByRefreshTokenHash(sha256(request.getRefreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (user.getRefreshTokenExpiresAt() == null || user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            user.setRefreshTokenHash(null);
            user.setRefreshTokenExpiresAt(null);
            userRepository.save(user);
            throw new UnauthorizedException("Refresh token expired, please log in again");
        }
        ensureCanLogin(user);
        // Rotation: the old refresh token stops working
        return issueTokens(user);
    }

    public MessageResponse logout(User currentUser) {
        User user = getUser(currentUser.getId());
        user.setRefreshTokenHash(null);
        user.setRefreshTokenExpiresAt(null);
        userRepository.save(user);
        return new MessageResponse("Logged out");
    }

    public UserResponse me(User currentUser) {
        return userService.getById(currentUser.getId());
    }

    /**
     * Profile edits from the account page. An investor who sends a new CR number (or resubmits after a
     * rejection) goes back to PENDING until an admin reviews it again.
     */
    public UserResponse updateProfile(User currentUser, UpdateProfileRequest request) {
        User user = getUser(currentUser.getId());
        if (user.getRole() == Role.PENDING) {
            throw new BadRequestException("Choose your membership type first");
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new DuplicateResourceException("Phone is already registered");
            }
            user.setPhone(request.getPhone());
        }
        if (request.getWhatsappOptIn() != null) {
            user.setWhatsappOptIn(request.getWhatsappOptIn());
        }
        if (request.getNeighborhoodId() != null) {
            if (user.getRole() != Role.RESIDENT) {
                throw new BadRequestException("Only residents have a home neighborhood");
            }
            user.setNeighborhood(findNeighborhood(request.getNeighborhoodId()));
        }

        boolean investorFields = request.getCrNumber() != null || request.getInvestmentBudget() != null
                || request.getInvestmentFields() != null;
        if (investorFields && user.getRole() != Role.INVESTOR) {
            throw new BadRequestException("Only investors have an investor profile");
        }
        if (request.getInvestmentBudget() != null) {
            user.setInvestmentBudget(request.getInvestmentBudget());
        }
        if (request.getInvestmentFields() != null) {
            user.setInvestmentFields(request.getInvestmentFields());
        }
        if (request.getCrNumber() != null) {
            String cr = request.getCrNumber().trim();
            boolean changed = !cr.equals(user.getCrNumber());
            if (changed || user.getInvestorStatus() == InvestorStatus.REJECTED) {
                // A different CR, or a resubmission after a rejection, needs a new admin check
                user.setCrNumber(cr);
                user.setInvestorStatus(InvestorStatus.PENDING);
                user.setInvestorReviewedAt(null);
            }
        }
        return userService.toResponse(userRepository.save(user));
    }

    // ---------- Google ----------

    /** Called by OAuth2LoginSuccessHandler after Google confirms the user. */
    public AuthResponse loginWithGoogle(String googleId, String email, String fullName) {
        if (email == null) {
            throw new BadRequestException("Google account has no email");
        }
        String normalizedEmail = normalizeEmail(email);

        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .orElse(null);

        if (user == null) {
            // New Google user: no password, email already verified by Google, role chosen next
            user = User.builder()
                    .fullName(fullName != null ? fullName : normalizedEmail)
                    .email(normalizedEmail)
                    .authProvider(AuthProvider.GOOGLE)
                    .googleId(googleId)
                    .role(Role.PENDING)
                    .emailVerified(true)
                    .enabled(true)
                    .build();
        } else {
            // Existing email account: link it to Google
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
            }
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setEmailVerified(true);
                user.setEnabled(true);
            }
        }
        userRepository.save(user);
        ensureCanLogin(user);
        return issueTokens(user);
    }

    /** PENDING (Google) users choose RESIDENT or INVESTOR once. A new token is returned with the new role. */
    public AuthResponse selectRole(User currentUser, SelectRoleRequest request) {
        User user = getUser(currentUser.getId());

        if (user.getRole() != Role.PENDING) {
            throw new BadRequestException("Membership type is already chosen");
        }
        Integer neighborhoodId = request.getNeighborhoodId() != null
                ? request.getNeighborhoodId()
                : (user.getNeighborhood() != null ? user.getNeighborhood().getId() : null);
        neighborhoodId = resolveNeighborhoodId(
                request.getRole(), neighborhoodId, request.getLatitude(), request.getLongitude());
        validateSignUpRole(request.getRole(), neighborhoodId);
        validateInvestorProfile(request.getRole(), request.getCrNumber(),
                request.getInvestmentBudget(), request.getInvestmentFields());

        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new DuplicateResourceException("Phone is already registered");
            }
            user.setPhone(request.getPhone());
        }
        if (neighborhoodId != null) {
            user.setNeighborhood(findNeighborhood(neighborhoodId));
        }
        if (request.getLatitude() != null && request.getLongitude() != null) {
            user.setLatitude(request.getLatitude());
            user.setLongitude(request.getLongitude());
        }
        user.setRole(request.getRole());
        applyInvestorProfile(user, request.getRole(), request.getCrNumber(),
                request.getInvestmentBudget(), request.getInvestmentFields());
        userRepository.save(user);
        return issueTokens(user);
    }

    // ---------- Location ----------

    /** Saves the user's current location; a resident without a neighborhood gets the nearest one. */
    public UserResponse updateLocation(User currentUser, UpdateLocationRequest request) {
        User user = getUser(currentUser.getId());
        user.setLatitude(request.getLatitude());
        user.setLongitude(request.getLongitude());
        if (user.getNeighborhood() == null && user.getRole() == Role.RESIDENT) {
            locationService.findNearestNeighborhood(request.getLatitude(), request.getLongitude())
                    .ifPresent(user::setNeighborhood);
        }
        return userService.toResponse(userRepository.save(user));
    }

    // ---------- Helpers ----------

    private AuthResponse issueTokens(User user) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        user.setRefreshTokenHash(sha256(refreshToken));
        user.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenSeconds())
                .roleSelectionRequired(user.getRole() == Role.PENDING)
                .user(userService.toResponse(user))
                .build();
    }

    private void ensureCanLogin(User user) {
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new ForbiddenException("This account is disabled");
        }
    }

    private void validateSignUpRole(Role role, Integer neighborhoodId) {
        if (role != Role.RESIDENT && role != Role.INVESTOR) {
            throw new BadRequestException("Role must be RESIDENT or INVESTOR");
        }
        if (role == Role.RESIDENT && neighborhoodId == null) {
            throw new BadRequestException("Neighborhood is required for residents");
        }
    }

    // A resident who didn't pick a neighborhood gets the one nearest to their location
    private Integer resolveNeighborhoodId(Role role, Integer neighborhoodId, Double latitude, Double longitude) {
        if (neighborhoodId != null || role != Role.RESIDENT || latitude == null || longitude == null) {
            return neighborhoodId;
        }
        return locationService.findNearestNeighborhood(latitude, longitude)
                .map(Neighborhood::getId)
                .orElseThrow(() -> new BadRequestException(
                        "Naseej isn't available in your area yet. Choose your neighborhood from the list"));
    }

    private void validateInvestorProfile(Role role, String crNumber, String budget, String fields) {
        if (role != Role.INVESTOR) {
            return;
        }
        if (crNumber == null || crNumber.isBlank()) {
            throw new BadRequestException("Commercial registration number is required for investors");
        }
        if (budget == null || budget.isBlank()) {
            throw new BadRequestException("Investment budget is required for investors");
        }
        if (fields == null || fields.isBlank()) {
            throw new BadRequestException("Choose at least one investment field");
        }
    }

    // Investors start as PENDING until an admin checks the CR; other roles have no investor data
    private void applyInvestorProfile(User user, Role role, String crNumber, String budget, String fields) {
        if (role == Role.INVESTOR) {
            user.setCrNumber(crNumber.trim());
            user.setInvestmentBudget(budget);
            user.setInvestmentFields(fields);
            user.setInvestorStatus(InvestorStatus.PENDING);
        } else {
            user.setCrNumber(null);
            user.setInvestmentBudget(null);
            user.setInvestmentFields(null);
            user.setInvestorStatus(null);
        }
    }

    private Neighborhood findNeighborhood(Integer id) {
        if (id == null) {
            return null;
        }
        return neighborhoodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Neighborhood not found with id: " + id));
    }

    private User getUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}