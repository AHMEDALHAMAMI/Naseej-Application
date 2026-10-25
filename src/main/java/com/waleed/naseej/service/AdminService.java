package com.waleed.naseej.service;

import com.waleed.naseej.dto.out.AdminStatsResponse;
import com.waleed.naseej.dto.out.IdeaResponse;
import com.waleed.naseej.dto.out.LabelCountResponse;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.AccessType;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Investor verification and the dashboard numbers. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int VOTE_DAYS = 30;

    private final UserRepository userRepository;
    private final IdeaRepository ideaRepository;
    private final VoteRepository voteRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final PaymentRepository paymentRepository;
    private final UserService userService;

    // ---------- Investor verification ----------

    /** status: PENDING (includes investors from before verification existed), VERIFIED, REJECTED or ALL. */
    public List<UserResponse> investors(String status) {
        String value = status == null ? "PENDING" : status.trim().toUpperCase();
        List<User> users;
        switch (value) {
            case "ALL" -> users = userRepository.findByRoleOrderByIdDesc(Role.INVESTOR);
            case "PENDING" -> {
                users = new ArrayList<>(userRepository.findByRoleAndInvestorStatusOrderByIdDesc(Role.INVESTOR, InvestorStatus.PENDING));
                users.addAll(userRepository.findByRoleAndInvestorStatusIsNullOrderByIdDesc(Role.INVESTOR));
            }
            case "VERIFIED", "REJECTED" -> users = userRepository.findByRoleAndInvestorStatusOrderByIdDesc(
                    Role.INVESTOR, InvestorStatus.valueOf(value));
            default -> throw new BadRequestException("Status must be PENDING, VERIFIED, REJECTED or ALL");
        }
        return users.stream().map(userService::toResponse).toList();
    }

    public UserResponse verifyInvestor(Integer id) {
        return review(id, InvestorStatus.VERIFIED);
    }

    public UserResponse rejectInvestor(Integer id) {
        return review(id, InvestorStatus.REJECTED);
    }

    // ---------- Moderation ----------

    /**
     * Disables (or re-enables) an account. A disabled user can't log in or refresh, and the JWT filter rejects
     * their current token on the next request. Admins can't be disabled here.
     */
    public UserResponse setUserDisabled(User currentAdmin, Integer id, boolean disabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (user.getId().equals(currentAdmin.getId())) {
            throw new BadRequestException("You can't disable your own account");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts can't be disabled here");
        }
        if (disabled && !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BadRequestException("This account hasn't verified its email yet, so it can't log in anyway");
        }
        user.setEnabled(!disabled);
        if (disabled) {
            // Ends every session: the refresh token stops working
            user.setRefreshTokenHash(null);
            user.setRefreshTokenExpiresAt(null);
        }
        return userService.toResponse(userRepository.save(user));
    }

    /**
     * Hides an idea from the map, the investor lists and voting (spam, abuse), or puts it back.
     * Only ideas still collecting votes or waiting for an investor can be hidden; adopted ones have a contract.
     */
    public IdeaResponse setIdeaHidden(Integer id, boolean hidden) {
        Idea idea = ideaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + id));
        if (hidden) {
            if (idea.getStatus() != IdeaStatus.PUBLISHED && idea.getStatus() != IdeaStatus.COMPLETED) {
                throw new BadRequestException("Only published or completed ideas can be hidden (this one is "
                        + idea.getStatus() + ")");
            }
            idea.setHiddenFromStatus(idea.getStatus());
            idea.setStatus(IdeaStatus.HIDDEN);
        } else {
            if (idea.getStatus() != IdeaStatus.HIDDEN) {
                throw new BadRequestException("This idea isn't hidden");
            }
            idea.setStatus(idea.getHiddenFromStatus() != null ? idea.getHiddenFromStatus() : IdeaStatus.PUBLISHED);
            idea.setHiddenFromStatus(null);
        }
        Idea saved = ideaRepository.save(idea);
        return IdeaResponse.builder()
                .id(saved.getId())
                .ownerId(saved.getOwner().getId())
                .neighborhoodId(saved.getNeighborhood().getId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .category(saved.getCategory())
                .status(saved.getStatus())
                .membershipThreshold(saved.getMembershipThreshold())
                .publishedAt(saved.getPublishedAt())
                .confirmedAt(saved.getConfirmedAt())
                .build();
    }

    private UserResponse review(Integer id, InvestorStatus status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (user.getRole() != Role.INVESTOR) {
            throw new BadRequestException("This user isn't an investor");
        }
        user.setInvestorStatus(status);
        user.setInvestorReviewedAt(LocalDateTime.now());
        return userService.toResponse(userRepository.save(user));
    }

    // ---------- Dashboard ----------

    public AdminStatsResponse stats() {
        List<LabelCountResponse> usersByRole = Arrays.stream(Role.values())
                .map(role -> count(role.name(), userRepository.countByRole(role)))
                .toList();

        List<User> investors = userRepository.findByRoleOrderByIdDesc(Role.INVESTOR);
        Map<String, Long> byStatus = investors.stream().collect(Collectors.groupingBy(
                u -> u.getInvestorStatus() == null ? InvestorStatus.PENDING.name() : u.getInvestorStatus().name(),
                Collectors.counting()));
        List<LabelCountResponse> investorsByStatus = Arrays.stream(InvestorStatus.values())
                .map(s -> count(s.name(), byStatus.getOrDefault(s.name(), 0L)))
                .toList();

        List<LabelCountResponse> ideasByStatus = Arrays.stream(IdeaStatus.values())
                .map(s -> count(s.name(), ideaRepository.countByStatus(s)))
                .toList();

        // Votes per day for the last 30 days, days without votes included as 0
        LocalDate firstDay = LocalDate.now().minusDays(VOTE_DAYS - 1L);
        Map<LocalDate, Long> perDay = voteRepository.findByCreatedAtAfter(firstDay.atStartOfDay()).stream()
                .collect(Collectors.groupingBy(v -> v.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<LabelCountResponse> votesPerDay = new ArrayList<>();
        for (int i = 0; i < VOTE_DAYS; i++) {
            LocalDate day = firstDay.plusDays(i);
            votesPerDay.add(count(day.toString(), perDay.getOrDefault(day, 0L)));
        }

        List<LabelCountResponse> accessByType = Arrays.stream(AccessType.values())
                .map(t -> count(t.name(), opportunityAccessRepository.countByAccessType(t)))
                .toList();

        // Neighborhoods with the most ideas that passed the check
        List<Idea> live = ideaRepository.findByStatusIn(List.of(
                IdeaStatus.PUBLISHED, IdeaStatus.COMPLETED, IdeaStatus.ADOPTED, IdeaStatus.OPENED));
        List<LabelCountResponse> topNeighborhoods = live.stream()
                .collect(Collectors.groupingBy(
                        idea -> idea.getNeighborhood().getName() + ", " + idea.getNeighborhood().getCity(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .map(e -> count(e.getKey(), e.getValue()))
                .toList();

        return AdminStatsResponse.builder()
                .usersByRole(usersByRole)
                .investorsByStatus(investorsByStatus)
                .ideasByStatus(ideasByStatus)
                .votesPerDay(votesPerDay)
                .accessByType(accessByType)
                .topNeighborhoods(topNeighborhoods)
                .totalVotes(voteRepository.count())
                .pendingInvestors(byStatus.getOrDefault(InvestorStatus.PENDING.name(), 0L))
                .paidRevenue(paymentRepository.sumAmountByStatus(PaymentStatus.PAID))
                .build();
    }

    private static LabelCountResponse count(String label, long count) {
        return LabelCountResponse.builder().label(label).count(count).build();
    }
}
