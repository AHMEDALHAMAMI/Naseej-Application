package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ProposeIdeaRequest;
import com.waleed.naseej.dto.in.UpdateIdeaRequest;
import com.waleed.naseej.dto.in.VoteRequest;
import com.waleed.naseej.dto.out.IdeaCheckResponse;
import com.waleed.naseej.dto.out.IdeaDetailResponse;
import com.waleed.naseej.dto.out.MapIdeaResponse;
import com.waleed.naseej.dto.out.MessageResponse;
import com.waleed.naseej.entity.FoundingMember;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.IdeaCheck;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.entity.Vote;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.enums.ScreeningDecision;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.BusinessModelRepository;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.FeasibilityReportRepository;
import com.waleed.naseej.repository.FoundingMemberRepository;
import com.waleed.naseej.repository.IdeaCheckRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NearbyPlaceRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The resident and investor side of ideas: the map around the user, the idea page (filtered by who is looking),
 * proposing an idea (AI check → published or rejected), voting inside the voting radius (and taking a vote back),
 * and the founder editing or withdrawing an idea.
 */
@Service
@RequiredArgsConstructor
public class DiscoverService {

    // A resident can vote only for ideas this close to them
    public static final double VOTING_RADIUS_KM = 2.0;

    // The resident map shows ideas up to this distance (outside the radius they are view-only)
    private static final double MAP_RADIUS_KM = 10.0;

    // The resident map also shows adopted and opened ideas, so neighbors see what is coming
    private static final List<IdeaStatus> VISIBLE_STATUSES = List.of(
            IdeaStatus.PUBLISHED, IdeaStatus.COMPLETED, IdeaStatus.ADOPTED, IdeaStatus.OPENED);

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final VoteRepository voteRepository;
    private final FoundingMemberRepository foundingMemberRepository;
    private final IdeaCheckRepository ideaCheckRepository;
    private final BusinessModelRepository businessModelRepository;
    private final FeasibilityReportRepository feasibilityReportRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final IdeaCheckService ideaCheckService;
    private final BusinessModelService businessModelService;
    private final FeasibilityReportService feasibilityReportService;
    private final NearbyPlaceService nearbyPlaceService;
    private final LocationService locationService;
    private final ContractRepository contractRepository;
    private final NearbyPlaceRepository nearbyPlaceRepository;
    private final N8nService n8nService;

    // ---------- Map ----------

    /** Open ideas around the user. Only titles and places, never vote counts. */
    public List<MapIdeaResponse> map(User currentUser, Double latitude, Double longitude) {
        User user = getUser(currentUser.getId());
        double[] origin = originOf(user, latitude, longitude);

        List<Idea> nearby = ideaRepository.findByStatusIn(VISIBLE_STATUSES).stream()
                .filter(idea -> LocationService.pointOf(idea) != null)
                .filter(idea -> distanceTo(origin, idea) <= MAP_RADIUS_KM)
                .sorted(Comparator.comparingDouble(idea -> distanceTo(origin, idea)))
                .toList();

        Set<Integer> voted = nearby.isEmpty()
                ? Set.of()
                : new HashSet<>(voteRepository.findVotedIdeaIds(user.getId(), nearby.stream().map(Idea::getId).toList()));

        return nearby.stream().map(idea -> {
            double[] point = LocationService.pointOf(idea);
            double km = distanceTo(origin, idea);
            boolean mine = idea.getOwner().getId().equals(user.getId());
            boolean inRange = km <= VOTING_RADIUS_KM;
            boolean hasVoted = voted.contains(idea.getId());
            return MapIdeaResponse.builder()
                    .id(idea.getId())
                    .title(idea.getTitle())
                    .category(idea.getCategory())
                    .status(idea.getStatus().name())
                    .neighborhoodName(idea.getNeighborhood().getName())
                    .latitude(point[0])
                    .longitude(point[1])
                    .distanceKm(LocationService.round2(km))
                    .inRange(inRange)
                    .voted(hasVoted)
                    .mine(mine)
                    .canVote(user.getRole() == Role.RESIDENT && inRange && !mine && !hasVoted
                            && idea.getStatus() == IdeaStatus.PUBLISHED)
                    .build();
        }).toList();
    }

    /** Ideas the user created, newest first (rejected ones included so the founder sees why). */
    public List<IdeaDetailResponse> myIdeas(User currentUser) {
        User user = getUser(currentUser.getId());
        return ideaRepository.findByOwnerIdOrderByIdDesc(user.getId()).stream()
                .map(idea -> buildDetail(idea, user, null, null, List.of()))
                .toList();
    }

    // ---------- Idea page ----------

    public IdeaDetailResponse detail(User currentUser, Integer ideaId, Double latitude, Double longitude) {
        User user = getUser(currentUser.getId());
        Idea idea = getIdea(ideaId);
        boolean founder = idea.getOwner().getId().equals(user.getId());
        boolean hidden = idea.getStatus() == IdeaStatus.SCREENING || idea.getStatus() == IdeaStatus.REJECTED
                || idea.getStatus() == IdeaStatus.HIDDEN;
        if (hidden && !founder && user.getRole() != Role.ADMIN) {
            throw new ResourceNotFoundException("Idea not found with id: " + ideaId);
        }
        return buildDetail(idea, user, latitude, longitude, List.of());
    }

    // ---------- Propose ----------

    /**
     * Creates the idea, runs the AI check, then publishes it (FIT) or rejects it (SIMILAR / SATURATED).
     * A published idea gets its business model, and an investor's idea also gets its feasibility report.
     * If the check itself fails (AI or Google down) the idea is removed so the user can simply try again.
     */
    public IdeaDetailResponse propose(User currentUser, ProposeIdeaRequest request) {
        User user = getUser(currentUser.getId());
        if (user.getRole() != Role.RESIDENT && user.getRole() != Role.INVESTOR) {
            throw new ForbiddenException("Only residents and investors can propose ideas");
        }

        Double latitude = request.getLatitude() != null ? request.getLatitude() : user.getLatitude();
        Double longitude = request.getLongitude() != null ? request.getLongitude() : user.getLongitude();
        if ((latitude == null) != (longitude == null)) {
            throw new BadRequestException("Send both latitude and longitude, or neither");
        }
        Neighborhood neighborhood = resolveNeighborhood(user, request.getNeighborhoodId(), latitude, longitude);

        Idea idea = ideaRepository.save(Idea.builder()
                .owner(user)
                .neighborhood(neighborhood)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory().trim())
                .latitude(latitude)
                .longitude(longitude)
                .status(IdeaStatus.SCREENING)
                .build());

        // The creator is the idea's only founding member
        FoundingMember founder = foundingMemberRepository.save(FoundingMember.builder()
                .user(user)
                .idea(idea)
                .memberNumber(1)
                .ideaOwner(true)
                .consentGiven(true)
                .build());

        IdeaCheckResponse check;
        try {
            check = ideaCheckService.run(idea.getId());
        } catch (RuntimeException ex) {
            foundingMemberRepository.delete(founder);
            ideaRepository.delete(idea);
            throw ex;
        }

        List<String> warnings = new ArrayList<>();
        if (Boolean.TRUE.equals(check.getCompetitorsUnavailable())) {
            warnings.add("The map service didn't answer, so nearby competitors weren't counted for this check");
        }
        Idea checked = getIdea(idea.getId());
        if (ScreeningDecision.FIT.name().equals(check.getDecision())) {
            checked.setStatus(IdeaStatus.PUBLISHED);
            checked.setPublishedAt(LocalDateTime.now());
            ideaRepository.save(checked);
            try {
                businessModelService.generate(checked.getId());
            } catch (RuntimeException ex) {
                warnings.add("The business model couldn't be generated right now: " + ex.getMessage());
            }
            if (user.getRole() == Role.INVESTOR) {
                try {
                    feasibilityReportService.generate(checked.getId());
                } catch (RuntimeException ex) {
                    warnings.add("The feasibility report couldn't be generated right now: " + ex.getMessage());
                }
            }
        } else {
            checked.setStatus(IdeaStatus.REJECTED);
            checked.setRejectionReason(rejectionReason(check));
            ideaRepository.save(checked);
        }
        return buildDetail(getIdea(idea.getId()), user, latitude, longitude, warnings);
    }

    /** Founder or investor asks again for the business model / report when the first try failed. */
    public IdeaDetailResponse retryAi(User currentUser, Integer ideaId) {
        User user = getUser(currentUser.getId());
        Idea idea = getIdea(ideaId);
        if (!idea.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the idea's creator can do this");
        }
        if (idea.getStatus() == IdeaStatus.SCREENING || idea.getStatus() == IdeaStatus.REJECTED) {
            throw new BadRequestException("This idea isn't published");
        }
        List<String> warnings = new ArrayList<>();
        if (!businessModelRepository.existsByIdeaId(ideaId)) {
            try {
                businessModelService.generate(ideaId);
            } catch (RuntimeException ex) {
                warnings.add("The business model couldn't be generated right now: " + ex.getMessage());
            }
        }
        if (user.getRole() == Role.INVESTOR && feasibilityReportRepository.findByIdeaId(ideaId).isEmpty()) {
            try {
                feasibilityReportService.generate(ideaId);
            } catch (RuntimeException ex) {
                warnings.add("The feasibility report couldn't be generated right now: " + ex.getMessage());
            }
        }
        return buildDetail(getIdea(ideaId), user, null, null, warnings);
    }

    // ---------- Vote ----------

    /**
     * A resident votes for a published idea inside their voting radius.
     * When the votes reach the idea's threshold the request is COMPLETED and investors can unlock it.
     */
    @Transactional
    public IdeaDetailResponse vote(User currentUser, Integer ideaId, VoteRequest request) {
        User user = getUser(currentUser.getId());
        if (user.getRole() != Role.RESIDENT) {
            throw new ForbiddenException("Only residents can vote");
        }
        Idea idea = getIdea(ideaId);
        if (idea.getOwner().getId().equals(user.getId())) {
            throw new BadRequestException("You are this idea's founding member, you can't vote for it");
        }
        if (idea.getStatus() != IdeaStatus.PUBLISHED) {
            throw new BadRequestException("Voting is closed for this idea");
        }
        if (voteRepository.existsByUserIdAndIdeaId(user.getId(), ideaId)) {
            throw new DuplicateResourceException("You already voted for this idea");
        }

        if (request != null && request.getLatitude() != null && request.getLongitude() != null) {
            user.setLatitude(request.getLatitude());
            user.setLongitude(request.getLongitude());
            userRepository.save(user);
        }
        double[] origin = originOf(user, null, null);
        double km = distanceTo(origin, idea);
        if (km > VOTING_RADIUS_KM) {
            throw new BadRequestException("You can vote only for ideas within " + VOTING_RADIUS_KM
                    + " km of you. This one is " + LocationService.round2(km) + " km away");
        }

        voteRepository.save(Vote.builder()
                .user(user)
                .idea(idea)
                .distanceKm(LocationService.round2(km))
                .build());

        long votes = voteRepository.countByIdeaId(ideaId);
        if (votes >= threshold(idea)) {
            idea.setStatus(IdeaStatus.COMPLETED);
            idea.setConfirmedAt(LocalDateTime.now());
            ideaRepository.save(idea);
            // n8n generates the alerts: investors interested in the neighborhood, and the founder
            n8nService.sendEvent("IDEA_COMPLETED", completedEventData(idea, votes));
        }
        return buildDetail(idea, user, null, null, List.of());
    }

    /** A resident takes their vote back. Only while the idea is still collecting votes. */
    @Transactional
    public IdeaDetailResponse unvote(User currentUser, Integer ideaId) {
        User user = getUser(currentUser.getId());
        if (user.getRole() != Role.RESIDENT) {
            throw new ForbiddenException("Only residents vote");
        }
        Idea idea = getIdea(ideaId);
        Vote vote = voteRepository.findByUserIdAndIdeaId(user.getId(), ideaId)
                .orElseThrow(() -> new ResourceNotFoundException("You haven't voted for this idea"));
        if (idea.getStatus() != IdeaStatus.PUBLISHED) {
            throw new BadRequestException("Votes are locked once the idea reaches its target");
        }
        voteRepository.delete(vote);
        return buildDetail(idea, user, null, null, List.of());
    }

    /** Ideas the resident voted for, newest vote first, to follow them until they open. */
    public List<IdeaDetailResponse> myVotes(User currentUser) {
        User user = getUser(currentUser.getId());
        if (user.getRole() != Role.RESIDENT) {
            throw new ForbiddenException("Only residents vote");
        }
        return voteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(Vote::getIdea)
                .filter(idea -> idea.getStatus() != IdeaStatus.HIDDEN)
                .map(idea -> buildDetail(idea, user, null, null, List.of()))
                .toList();
    }

    // ---------- Founder: edit and withdraw ----------

    /**
     * The founder edits a published idea. The category and the place stay as the AI screened them,
     * and the title is locked once neighbors voted for it.
     */
    public IdeaDetailResponse updateIdea(User currentUser, Integer ideaId, UpdateIdeaRequest request) {
        User user = getUser(currentUser.getId());
        Idea idea = getIdea(ideaId);
        ensureFounder(idea, user);
        if (idea.getStatus() != IdeaStatus.PUBLISHED) {
            throw new BadRequestException("Only an idea that is still collecting votes can be edited");
        }
        String title = request.getTitle() == null || request.getTitle().isBlank() ? null : request.getTitle().trim();
        String description = request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().trim();
        if (title == null && description == null) {
            throw new BadRequestException("Send a new title or description");
        }
        if (title != null && !title.equals(idea.getTitle()) && voteRepository.countByIdeaId(ideaId) > 0) {
            throw new BadRequestException("Neighbors already voted for this title. You can still edit the description");
        }
        if (title != null) {
            idea.setTitle(title);
        }
        if (description != null) {
            idea.setDescription(description);
        }
        ideaRepository.save(idea);
        return buildDetail(getIdea(ideaId), user, null, null, List.of());
    }

    /**
     * The founder withdraws an idea that is still collecting votes, or cleans up a rejected one.
     * Its votes, AI check, competitors, business model and report go with it.
     */
    @Transactional
    public MessageResponse deleteIdea(User currentUser, Integer ideaId) {
        User user = getUser(currentUser.getId());
        Idea idea = getIdea(ideaId);
        ensureFounder(idea, user);
        if (idea.getStatus() != IdeaStatus.PUBLISHED && idea.getStatus() != IdeaStatus.REJECTED) {
            throw new BadRequestException("Only an idea that is still collecting votes, or a rejected one, can be withdrawn");
        }
        if (!contractRepository.findByIdeaId(ideaId).isEmpty() || opportunityAccessRepository.countByIdeaId(ideaId) > 0) {
            throw new BadRequestException("This idea has an investor history, so it can't be deleted");
        }

        // Other ideas' checks that pointed at this one as "similar" keep their result without the link
        ideaCheckRepository.findBySimilarIdeaId(ideaId).forEach(check -> {
            check.setSimilarIdea(null);
            ideaCheckRepository.save(check);
        });
        voteRepository.deleteByIdeaId(ideaId);
        nearbyPlaceRepository.deleteAllForIdea(ideaId);
        businessModelRepository.findByIdeaId(ideaId).ifPresent(businessModelRepository::delete);
        feasibilityReportRepository.findByIdeaId(ideaId).ifPresent(feasibilityReportRepository::delete);
        ideaCheckRepository.findByIdeaId(ideaId).ifPresent(ideaCheckRepository::delete);
        foundingMemberRepository.deleteByIdeaId(ideaId);
        ideaRepository.delete(idea);
        return new MessageResponse("Idea \"" + idea.getTitle() + "\" was withdrawn");
    }

    private static void ensureFounder(Idea idea, User user) {
        if (!idea.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the idea's creator can do this");
        }
    }

    private Map<String, Object> completedEventData(Idea idea, long votes) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ideaId", idea.getId());
        data.put("title", idea.getTitle());
        data.put("category", idea.getCategory());
        data.put("neighborhood", idea.getNeighborhood().getName());
        data.put("city", idea.getNeighborhood().getCity());
        data.put("votes", votes);
        data.put("threshold", threshold(idea));
        data.put("founderName", idea.getOwner().getFullName());
        data.put("founderEmail", idea.getOwner().getEmail());
        data.put("founderPhone", idea.getOwner().getPhone());
        data.put("founderWhatsappOptIn", idea.getOwner().getWhatsappOptIn());
        return data;
    }

    // ---------- Building the idea page ----------

    private IdeaDetailResponse buildDetail(Idea idea, User viewer, Double latitude, Double longitude,
                                           List<String> warnings) {
        boolean founder = idea.getOwner().getId().equals(viewer.getId());
        Role role = viewer.getRole();
        boolean investor = role == Role.INVESTOR;
        boolean admin = role == Role.ADMIN;
        boolean seesNumbers = founder || investor || admin;

        double[] point = LocationService.pointOf(idea);
        Neighborhood neighborhood = idea.getNeighborhood();

        IdeaDetailResponse.IdeaDetailResponseBuilder out = IdeaDetailResponse.builder()
                .id(idea.getId())
                .title(idea.getTitle())
                .description(idea.getDescription())
                .category(idea.getCategory())
                .status(idea.getStatus().name())
                .neighborhoodId(neighborhood.getId())
                .neighborhoodName(neighborhood.getName())
                .city(neighborhood.getCity())
                .latitude(point != null ? point[0] : null)
                .longitude(point != null ? point[1] : null)
                .locationLabel(LocationService.sideOf(idea))
                .publishedAt(idea.getPublishedAt())
                .confirmedAt(idea.getConfirmedAt())
                .founder(founder)
                .viewerRole(founder ? "FOUNDER" : (admin ? "ADMIN" : (investor ? "INVESTOR" : "VOTER")))
                .rejectionReason(founder || admin ? idea.getRejectionReason() : null)
                .warnings(warnings.isEmpty() ? null : warnings);

        // Voting: everyone sees whether they are in range; only residents can vote
        if (role == Role.RESIDENT) {
            boolean hasVoted = voteRepository.existsByUserIdAndIdeaId(viewer.getId(), idea.getId());
            Double km = null;
            boolean inRange = false;
            double[] origin = originOrNull(viewer, latitude, longitude);
            if (origin != null && point != null) {
                km = LocationService.round2(distanceTo(origin, idea));
                inRange = km <= VOTING_RADIUS_KM;
            }
            out.votingRadiusKm(VOTING_RADIUS_KM)
                    .distanceKm(km)
                    .inRange(inRange)
                    .voted(hasVoted)
                    .canVote(!founder && inRange && !hasVoted && idea.getStatus() == IdeaStatus.PUBLISHED)
                    .canUnvote(hasVoted && idea.getStatus() == IdeaStatus.PUBLISHED);
        }

        if (founder) {
            boolean collecting = idea.getStatus() == IdeaStatus.PUBLISHED;
            out.canEdit(collecting)
                    .canDelete(collecting || idea.getStatus() == IdeaStatus.REJECTED)
                    .canReviseBusinessModel(collecting && businessModelRepository.existsByIdeaId(idea.getId()));
        }

        if (seesNumbers) {
            long votes = voteRepository.countByIdeaId(idea.getId());
            out.votesCount(votes)
                    .membershipThreshold(threshold(idea))
                    .votePercent(percent(votes, threshold(idea)));
            if (businessModelRepository.existsByIdeaId(idea.getId())) {
                out.businessModel(businessModelService.getByIdea(idea.getId()));
            }
        }

        // AI check: residents see the summary and competitors, founders also see why it was rejected
        ideaCheckRepository.findByIdeaId(idea.getId()).ifPresent((IdeaCheck check) -> {
            out.checkDecision(check.getDecision())
                    .checkScore(check.getScore())
                    .scarcityFound(check.getScarcityFound())
                    .searchRadiusKm(check.getSearchRadiusKm())
                    .competitorsCount(check.getCompetitorsCount())
                    .nearestCompetitorKm(check.getNearestCompetitorKm())
                    .feasibilitySummary(check.getFeasibilitySummary())
                    .competitors(nearbyPlaceService.getByIdea(idea.getId()));
            if (check.getSimilarIdea() != null) {
                out.similarIdeaId(check.getSimilarIdea().getId())
                        .similarIdeaTitle(check.getSimilarIdea().getTitle());
            }
        });

        if (investor) {
            InvestorStatus status = viewer.getInvestorStatus() != null ? viewer.getInvestorStatus() : InvestorStatus.PENDING;
            // Only live contracts count: an expired or refunded adoption gives the idea back to the investors
            boolean unlocked = contractRepository.existsByIdeaIdAndInvestorIdAndStatusNot(
                    idea.getId(), viewer.getId(), ContractStatus.TERMINATED);
            boolean adopted = contractRepository.existsByIdeaIdAndStatusNot(idea.getId(), ContractStatus.TERMINATED);
            boolean heldBefore = !unlocked
                    && opportunityAccessRepository.existsByInvestorIdAndIdeaId(viewer.getId(), idea.getId());
            out.investorStatus(status)
                    .unlocked(unlocked)
                    .adopted(adopted);
            feasibilityReportRepository.findByIdeaId(idea.getId())
                    .ifPresent(report -> out.gapScore(report.getGapScore()));
            if (unlocked && feasibilityReportRepository.findByIdeaId(idea.getId()).isPresent()) {
                out.feasibilityReport(feasibilityReportService.getFull(idea.getId(), viewer.getId()));
            }

            String blocked = null;
            if (status != InvestorStatus.VERIFIED && viewer.getCrNumber() == null) {
                blocked = "Add your commercial registration number to unlock opportunities";
            } else if (status != InvestorStatus.VERIFIED) {
                blocked = status == InvestorStatus.REJECTED
                        ? "Your investor account wasn't approved"
                        : "Your account is under review. You can unlock after the admin verifies your CR";
            } else if (adopted) {
                blocked = unlocked ? "You already adopted this idea" : "Another investor already adopted this idea";
            } else if (heldBefore) {
                blocked = "Your earlier adoption of this idea ended, so it's now open to other investors";
            } else if (!founder && idea.getStatus() != IdeaStatus.COMPLETED) {
                blocked = "Unlocking opens when the idea reaches 100% of its vote target";
            }
            boolean openForOwner = idea.getStatus() == IdeaStatus.PUBLISHED || idea.getStatus() == IdeaStatus.COMPLETED;
            out.canUnlock(blocked == null && !founder)
                    .canAdopt(founder && status == InvestorStatus.VERIFIED && !adopted && !heldBefore && openForOwner)
                    .unlockBlockedReason(founder ? null : blocked);
        }
        return out.build();
    }

    // ---------- Helpers ----------

    private String rejectionReason(IdeaCheckResponse check) {
        if (ScreeningDecision.SIMILAR.name().equals(check.getDecision())) {
            String title = ideaRepository.findById(check.getSimilarIdeaId() != null ? check.getSimilarIdeaId() : -1)
                    .map(Idea::getTitle)
                    .orElse("another idea");
            return "A similar idea is already open nearby (\"" + title + "\"). Similar ideas split the votes, "
                    + "so vote for the existing one instead.";
        }
        String nearest = check.getNearestCompetitorKm() != null
                ? ", the nearest is " + LocationService.round2(check.getNearestCompetitorKm()) + " km away"
                : "";
        return "No shortage found: " + check.getCompetitorsCount() + " similar businesses within "
                + check.getSearchRadiusKm() + " km" + nearest + ".";
    }

    private Neighborhood resolveNeighborhood(User user, Integer neighborhoodId, Double latitude, Double longitude) {
        if (neighborhoodId != null) {
            return neighborhoodRepository.findById(neighborhoodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Neighborhood not found with id: " + neighborhoodId));
        }
        if (latitude != null && longitude != null) {
            var nearest = locationService.findNearestNeighborhood(latitude, longitude);
            if (nearest.isPresent()) {
                return nearest.get();
            }
        }
        if (user.getNeighborhood() != null) {
            return user.getNeighborhood();
        }
        throw new BadRequestException("Choose the neighborhood for this idea");
    }

    // Where the user is: the browser location if sent, else the saved one, else their neighborhood center
    private double[] originOf(User user, Double latitude, Double longitude) {
        double[] origin = originOrNull(user, latitude, longitude);
        if (origin == null) {
            throw new BadRequestException("Share your location to see ideas around you");
        }
        return origin;
    }

    private double[] originOrNull(User user, Double latitude, Double longitude) {
        if (latitude != null && longitude != null) {
            return new double[]{latitude, longitude};
        }
        if (user.getLatitude() != null && user.getLongitude() != null) {
            return new double[]{user.getLatitude(), user.getLongitude()};
        }
        Neighborhood home = user.getNeighborhood();
        if (home != null && home.getLatitude() != null && home.getLongitude() != null) {
            return new double[]{home.getLatitude(), home.getLongitude()};
        }
        return null;
    }

    private static double distanceTo(double[] origin, Idea idea) {
        double[] point = LocationService.pointOf(idea);
        if (point == null) {
            return Double.MAX_VALUE;
        }
        return LocationService.distanceKm(origin[0], origin[1], point[0], point[1]);
    }

    static int threshold(Idea idea) {
        return idea.getMembershipThreshold() != null && idea.getMembershipThreshold() > 0
                ? idea.getMembershipThreshold() : 150;
    }

    static int percent(long votes, int threshold) {
        return (int) Math.min(100, Math.round(votes * 100.0 / threshold));
    }

    private User getUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private Idea getIdea(Integer id) {
        return ideaRepository.findWithNeighborhoodById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + id));
    }
}
