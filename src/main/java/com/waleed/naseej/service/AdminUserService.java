package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.AdminPasswordRequest;
import com.waleed.naseej.dto.in.AdminUserRequest;
import com.waleed.naseej.dto.in.AdminUserUpdateRequest;
import com.waleed.naseej.dto.out.AdminUserResponse;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.AddressRepository;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.ContractSignatureRepository;
import com.waleed.naseej.repository.FoundingMemberRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.SubscriptionRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin's members list and what an admin can do with an account: add, edit, suspend, reactivate,
 * set a new password and delete.
 * Safety rules: an admin can't suspend, delete or demote themselves, and the last active admin always stays.
 * A member who has ideas, payments, contracts or subscriptions can't be deleted (that history belongs to other
 * people too), only suspended. Deleting a member removes their votes and founding memberships with them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final IdeaRepository ideaRepository;
    private final VoteRepository voteRepository;
    private final FoundingMemberRepository foundingMemberRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final PaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final ContractSignatureRepository contractSignatureRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    // ---------- List ----------

    /** Everyone who signed up (residents, investors, Google users who haven't chosen yet, admins), newest first. */
    public List<AdminUserResponse> users() {
        Map<Integer, Long> ideas = toMap(ideaRepository.countPerOwner());
        Map<Integer, Long> votes = toMap(voteRepository.countPerUser());
        return userRepository.findAllByOrderByIdDesc().stream()
                .map(u -> toResponse(u, ideas.getOrDefault(u.getId(), 0L), votes.getOrDefault(u.getId(), 0L)))
                .toList();
    }

    // ---------- Add ----------

    public AdminUserResponse create(User admin, AdminUserRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        String phone = blankToNull(request.getPhone());
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone is already registered");
        }
        Role role = checkRole(request.getRole());
        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(phone)
                .authProvider(AuthProvider.LOCAL)
                .role(role)
                .emailVerified(true)
                .enabled(true)
                .build();
        applyMembership(user, role, request.getNeighborhoodId(), request.getCrNumber(), request.getInvestorStatus());
        user = userRepository.save(user);
        log.info("Admin {} added member {} ({})", admin.getEmail(), user.getEmail(), role);
        return one(user);
    }

    // ---------- Edit ----------

    public AdminUserResponse update(User admin, Integer id, AdminUserUpdateRequest request) {
        User user = getUser(id);
        Role role = checkRole(request.getRole());
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN) {
            if (user.getId().equals(admin.getId())) {
                throw new BadRequestException("You can't remove your own admin access");
            }
            ensureAnotherActiveAdmin(user);
        }
        String phone = blankToNull(request.getPhone());
        if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone is already registered");
        }
        user.setFullName(request.getFullName().trim());
        user.setPhone(phone);
        if (request.getEmailVerified() != null) {
            user.setEmailVerified(request.getEmailVerified());
        }
        boolean investorChanged = role == Role.INVESTOR && request.getInvestorStatus() != null
                && request.getInvestorStatus() != user.getInvestorStatus();
        user.setRole(role);
        applyMembership(user, role, request.getNeighborhoodId(), request.getCrNumber(), request.getInvestorStatus());
        if (investorChanged) {
            user.setInvestorReviewedAt(LocalDateTime.now());
        }
        userRepository.save(user);
        log.info("Admin {} edited member {} ({})", admin.getEmail(), user.getEmail(), role);
        return one(user);
    }

    // ---------- Suspend / reactivate ----------

    /** A suspended member can't log in, and their open sessions stop working on the next request. */
    public AdminUserResponse suspend(User admin, Integer id) {
        User user = getUser(id);
        if (user.getId().equals(admin.getId())) {
            throw new BadRequestException("You can't suspend your own account");
        }
        if (user.getRole() == Role.ADMIN) {
            ensureAnotherActiveAdmin(user);
        }
        user.setEnabled(false);
        endSessions(user);
        userRepository.save(user);
        log.info("Admin {} suspended member {}", admin.getEmail(), user.getEmail());
        return one(user);
    }

    public AdminUserResponse reactivate(User admin, Integer id) {
        User user = getUser(id);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Admin {} reactivated member {}", admin.getEmail(), user.getEmail());
        return one(user);
    }

    // ---------- Password ----------

    public AdminUserResponse setPassword(User admin, Integer id, AdminPasswordRequest request) {
        User user = getUser(id);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        endSessions(user);
        userRepository.save(user);
        log.info("Admin {} set a new password for {}", admin.getEmail(), user.getEmail());
        return one(user);
    }

    // ---------- Delete ----------

    @Transactional
    public void delete(User admin, Integer id) {
        User user = getUser(id);
        if (user.getId().equals(admin.getId())) {
            throw new BadRequestException("You can't delete your own account");
        }
        if (user.getRole() == Role.ADMIN) {
            ensureAnotherActiveAdmin(user);
        }
        List<String> history = new ArrayList<>();
        if (ideaRepository.existsByOwnerId(id)) history.add("ideas");
        if (paymentRepository.existsByUserId(id)) history.add("payments");
        if (opportunityAccessRepository.existsByInvestorId(id)
                || contractRepository.existsByInvestorIdOrIdeaOwnerId(id, id)
                || contractSignatureRepository.existsBySignerId(id)) history.add("contracts");
        if (subscriptionRepository.existsByUserId(id)) history.add("subscriptions");
        if (!history.isEmpty()) {
            throw new BadRequestException("This member has " + String.join(", ", history)
                    + " on the platform, so the account can't be deleted. Suspend it instead");
        }
        voteRepository.deleteByUserId(id);
        addressRepository.deleteByUserId(id);
        foundingMemberRepository.deleteByUserId(id);
        userRepository.delete(user);
        log.info("Admin {} deleted member {}", admin.getEmail(), user.getEmail());
    }

    // ---------- Helpers ----------

    private Role checkRole(Role role) {
        if (role == null || role == Role.LANDLORD) {
            throw new BadRequestException("Membership must be RESIDENT, INVESTOR, ADMIN or PENDING");
        }
        return role;
    }

    // Residents need a neighborhood; investors keep a verification status; other roles drop the investor data
    private void applyMembership(User user, Role role, Integer neighborhoodId, String crNumber, InvestorStatus status) {
        if (neighborhoodId != null) {
            Neighborhood n = neighborhoodRepository.findById(neighborhoodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Neighborhood not found with id: " + neighborhoodId));
            user.setNeighborhood(n);
        }
        if (role == Role.RESIDENT && user.getNeighborhood() == null) {
            throw new BadRequestException("Neighborhood is required for residents");
        }
        if (role == Role.INVESTOR) {
            user.setCrNumber(blankToNull(crNumber) != null ? crNumber.trim() : user.getCrNumber());
            user.setInvestorStatus(status != null ? status
                    : (user.getInvestorStatus() != null ? user.getInvestorStatus() : InvestorStatus.PENDING));
        } else {
            user.setInvestorStatus(null);
        }
    }

    private void ensureAnotherActiveAdmin(User user) {
        boolean another = userRepository.findByRoleOrderByIdDesc(Role.ADMIN).stream()
                .anyMatch(a -> !a.getId().equals(user.getId()) && Boolean.TRUE.equals(a.getEnabled()));
        if (!another) {
            throw new BadRequestException("This is the last active admin. Add another admin first");
        }
    }

    private static void endSessions(User user) {
        user.setRefreshTokenHash(null);
        user.setRefreshTokenExpiresAt(null);
    }

    private AdminUserResponse one(User user) {
        return toResponse(user, ideaRepository.countByOwnerId(user.getId()), voteRepository.countByUserId(user.getId()));
    }

    private static AdminUserResponse toResponse(User u, long ideas, long votes) {
        return AdminUserResponse.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .role(u.getRole())
                .authProvider(u.getAuthProvider())
                .emailVerified(u.getEmailVerified())
                .enabled(u.getEnabled())
                .neighborhoodId(u.getNeighborhood() != null ? u.getNeighborhood().getId() : null)
                .neighborhoodName(u.getNeighborhood() != null ? u.getNeighborhood().getName() : null)
                .city(u.getNeighborhood() != null ? u.getNeighborhood().getCity() : null)
                .investorStatus(u.getRole() == Role.INVESTOR
                        ? (u.getInvestorStatus() != null ? u.getInvestorStatus() : InvestorStatus.PENDING) : null)
                .crNumber(u.getCrNumber())
                .ideasCount(ideas)
                .votesCount(votes)
                .build();
    }

    private static Map<Integer, Long> toMap(List<Object[]> rows) {
        Map<Integer, Long> map = new HashMap<>();
        rows.forEach(row -> map.put((Integer) row[0], (Long) row[1]));
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private User getUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
