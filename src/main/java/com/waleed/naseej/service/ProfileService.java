package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ProfileUpdateRequest;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The user's own profile: name, phone, language, and for investors the company details and budget range.
 * Email changes aren't offered here (they need a new verification code). Addresses are in AddressService.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final BigDecimal K300 = new BigDecimal("300000");
    private static final BigDecimal K700 = new BigDecimal("700000");

    private final UserRepository userRepository;
    private final UserService userService;

    public UserResponse get(User currentUser) {
        return userService.getById(currentUser.getId());
    }

    @Transactional
    public UserResponse update(User currentUser, ProfileUpdateRequest request) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUser.getId()));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            String phone = request.getPhone().isBlank() ? null : request.getPhone();
            if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
                throw new DuplicateResourceException("Phone is already registered");
            }
            user.setPhone(phone);
        }
        if (request.getPreferredLanguage() != null) {
            user.setPreferredLanguage(request.getPreferredLanguage());
        }

        boolean investorPart = request.getCompanyName() != null || request.getCrNumber() != null
                || request.getBudgetMin() != null || request.getBudgetMax() != null
                || request.getBudgetNoMax() != null || request.getInvestmentFields() != null;
        if (investorPart) {
            if (user.getRole() != Role.INVESTOR) {
                throw new BadRequestException("Only investors have an investor profile");
            }
            applyInvestor(user, request);
        }
        return userService.toResponse(userRepository.save(user));
    }

    private void applyInvestor(User user, ProfileUpdateRequest request) {
        if (request.getCompanyName() != null) {
            user.setCompanyName(request.getCompanyName().isBlank() ? null : request.getCompanyName().trim());
        }
        if (request.getCrNumber() != null && !request.getCrNumber().equals(user.getCrNumber())) {
            user.setCrNumber(request.getCrNumber());
            // A new CR has to be checked again before the investor can unlock
            if (user.getInvestorStatus() == InvestorStatus.VERIFIED || user.getInvestorStatus() == InvestorStatus.REJECTED) {
                user.setInvestorStatus(InvestorStatus.PENDING);
                user.setInvestorReviewedAt(null);
            }
        }
        if (request.getBudgetMin() != null) {
            BigDecimal max = Boolean.TRUE.equals(request.getBudgetNoMax()) ? null : request.getBudgetMax();
            if (max == null && !Boolean.TRUE.equals(request.getBudgetNoMax())) {
                throw new BadRequestException("Choose the maximum budget, or \"and above\"");
            }
            if (max != null && request.getBudgetMin().compareTo(max) > 0) {
                throw new BadRequestException("The minimum budget can't be more than the maximum");
            }
            user.setBudgetMin(request.getBudgetMin());
            user.setBudgetMax(max);
            user.setInvestmentBudget(bucketFor(max));
        }
        if (request.getInvestmentFields() != null) {
            user.setInvestmentFields(request.getInvestmentFields());
        }
    }

    // ---------- Budget buckets from before the range (older screens and reports still read them) ----------

    /** The older bucket that best matches a range's maximum (null = "and above"). */
    public static String bucketFor(BigDecimal max) {
        if (max == null) {
            return "ABOVE_700K";
        }
        if (max.compareTo(K300) <= 0) {
            return "UP_TO_300K";
        }
        return max.compareTo(K700) <= 0 ? "FROM_300K_TO_700K" : "ABOVE_700K";
    }

    /** A range for investors who chose a bucket before ranges existed. */
    public static BigDecimal bucketMin(String bucket) {
        if (bucket == null) {
            return null;
        }
        return switch (bucket) {
            case "FROM_300K_TO_700K" -> K300;
            case "ABOVE_700K" -> K700;
            default -> BigDecimal.ZERO;
        };
    }

    public static BigDecimal bucketMax(String bucket) {
        if (bucket == null) {
            return null;
        }
        return switch (bucket) {
            case "UP_TO_300K" -> K300;
            case "FROM_300K_TO_700K" -> K700;
            default -> null;
        };
    }
}
