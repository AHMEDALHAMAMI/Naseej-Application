package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ProposeIdeaRequest;
import com.waleed.naseej.dto.in.UpdateIdeaRequest;
import com.waleed.naseej.dto.in.VoteRequest;
import com.waleed.naseej.dto.out.BusinessModelResponse;
import com.waleed.naseej.dto.out.FeasibilityReportResponse;
import com.waleed.naseej.dto.out.IdeaCheckResponse;
import com.waleed.naseej.dto.out.IdeaDetailResponse;
import com.waleed.naseej.dto.out.MapIdeaResponse;
import com.waleed.naseej.dto.out.MessageResponse;
import com.waleed.naseej.entity.Address;
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
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The resident and investor side of ideas: the map around the user, the idea page (filtered by who is looking),
 * proposing an idea (AI check → draft → published, or rejected), voting inside the voting radius (and taking a
 * vote back), and the founder editing or withdrawing an idea.
 * Residents' eligibility always comes from their saved addresses (AddressService), never from the device's
 * location: coordinates sent by the browser are ignored for residents.
 */
@Service
@RequiredArgsConstructor
public class DiscoverService {

    // A resident can vote only for ideas this close to them
    public static final double VOTING_RADIUS_KM = 2.0;

    // The resident map shows ideas up to this distance (outside the radius they are view-only)
    private static final double MAP_RADIUS_KM = 10.0;

    // A resident proposes ideas for the area they live in; investors can pick any spot in the country
    public static final double RESIDENT_PROPOSE_RADIUS_KM = 5.0;
    // How many times an idea can be changed and checked again (each check calls the AI and the map services)
    public static final int MAX_RECHECKS = 3;
    // A draft whose check is older than this is checked again before publishing
    private static final long CHECK_FRESH_HOURS = 24;

    private static final int SUMMARY_LENGTH = 160;

    // The resident map also shows adopted and opened ideas, so neighbors see what is coming
    private static final List<IdeaStatus> VISIBLE_STATUSES = List.of(
            IdeaStatus.PUBLISHED, IdeaStatus.COMPLETED, IdeaStatus.ADOPTED, IdeaStatus.OPENED);

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final VoteRepository voteRepository;
    private final FoundingMemberRepository foundingMemberRepository;
    private final IdeaCheckRepository ideaCheckRepository;
    private final NearbyPlaceRepository nearbyPlaceRepository;
    private final BusinessModelRepository businessModelRepository;
    private final FeasibilityReportRepository feasibilityReportRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final IdeaCheckService ideaCheckService;
    private final BusinessModelService businessModelService;
    private final FeasibilityReportService feasibilityReportService;
    private final NearbyPlaceService nearbyPlaceService;
    private final LocationService locationService;
    private final ContractRepository contractRepository;
    private final N8nService n8nService;
    private final ProjectService projectService;
    private final AddressService addressService;
    private final AiTranslationService aiTranslationService;
    private final NotificationService notificationService;

    // ---------- Map ----------

    /**
     * Open ideas around the user. Only titles and places, never vote counts.
     * Residents: centered on the chosen saved address (addressId) or their primary one; an idea can be voted on
     * when it is within the voting radius of any of their addresses.
     */
    public List<MapIdeaResponse> map(User currentUser, Double latitude, Double longitude, Integer addressId) {
        User user = getUser(currentUser.getId());
        boolean resident = user.getRole() == Role.RESIDENT;
        List<Address> addresses = resident ? addressService.addressesOf(user) : List.of();
        double[] origin = resident ? residentOrigin(addresses, addressId) : originOf(user, latitude, longitude);
        boolean confirmed = addresses.stream().noneMatch(a -> Boolean.TRUE.equals(a.getNeedsConfirmation()));

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
            boolean inRange = resident ? eligibleAddress(addresses, idea) != null : km <= VOTING_RADIUS_KM;
            boolean hasVoted = voted.contains(idea.getId());
            var stage = projectService.stageOfIdea(idea).orElse(null);
            return MapIdeaResponse.builder()
                    .summary(shorten(idea.getDescription(), SUMMARY_LENGTH))
                    .stage(stage != null ? stage.stage() : null)
                    .stageIndex(stage != null ? stage.index() : null)
                    .stageCount(stage != null ? stage.count() : null)
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
                    .canVote(resident && confirmed && inRange && !mine && !hasVoted
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
        boolean hidden = idea.getStatus() == IdeaStatus.SCREENING || idea.getStatus() == IdeaStatus.DRAFT
                || idea.getStatus() == IdeaStatus.REJECTED || idea.getStatus() == IdeaStatus.HIDDEN;
        if (hidden && !founder && user.getRole() != Role.ADMIN) {
            throw new ResourceNotFoundException("Idea not found with id: " + ideaId);
        }
        // Residents see ideas of their own area (the map shows the same radius), so the full list can't be browsed
        if (user.getRole() == Role.RESIDENT && !founder) {
            double[] point = LocationService.pointOf(idea);
            List<Address> addresses = addressService.addressesOf(user);
            boolean near = point == null || addresses.isEmpty()
                    || addresses.stream().anyMatch(a -> AddressService.distanceKm(a, point) <= MAP_RADIUS_KM);
            if (!near && !voteRepository.existsByUserIdAndIdeaId(user.getId(), ideaId)) {
                throw new ForbiddenException("You can open ideas within " + (int) MAP_RADIUS_KM + " km of your home");
            }
        }
        return localized(buildDetail(idea, user, latitude, longitude, List.of()));
    }

    // ---------- Propose: write → AI check → review → publish ----------

    /**
     * Creates the idea and runs the AI check. A FIT idea becomes a private DRAFT with its business model,
     * so the author can review it and then publish or cancel it; a SIMILAR / SATURATED idea is REJECTED with the reason.
     * If the check itself fails (AI or Google down) the idea is removed so the user can simply try again.
     */
    public IdeaDetailResponse propose(User currentUser, ProposeIdeaRequest request) {
        User user = getUser(currentUser.getId());
        if (user.getRole() != Role.RESIDENT && user.getRole() != Role.INVESTOR) {
            throw new ForbiddenException("Only residents and investors can propose ideas");
        }
        Spot spot = placeOf(user, request);

        Idea idea = ideaRepository.save(Idea.builder()
                .owner(user)
                .neighborhood(spot.neighborhood())
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory().trim())
                .latitude(spot.latitude())
                .longitude(spot.longitude())
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
        List<String> warnings = applyCheck(getIdea(idea.getId()), check);
        return localized(buildDetail(getIdea(idea.getId()), user, spot.latitude(), spot.longitude(), warnings));
    }

    /**
     * Edit and recheck: the author changes a draft (or an idea that didn't pass) and the AI checks it again.
     * Each idea can be changed and rechecked MAX_RECHECKS times; checking the same text again (when the check
     * got too old to publish) doesn't count. If the check fails, the idea goes back to how it was.
     */
    public IdeaDetailResponse updateDraft(User currentUser, Integer ideaId, ProposeIdeaRequest request) {
        User user = getUser(currentUser.getId());
        Idea idea = ownIdea(user, ideaId);
        if (idea.getStatus() != IdeaStatus.DRAFT && idea.getStatus() != IdeaStatus.REJECTED) {
            throw new BadRequestException("Only drafts and ideas that didn't pass the check can be edited");
        }
        Spot spot = placeOf(user, request);
        boolean changed = !Objects.equals(idea.getTitle(), request.getTitle().trim())
                || !Objects.equals(idea.getDescription(), request.getDescription().trim())
                || !Objects.equals(idea.getCategory(), request.getCategory().trim())
                || !Objects.equals(idea.getLatitude(), spot.latitude())
                || !Objects.equals(idea.getLongitude(), spot.longitude())
                || !idea.getNeighborhood().getId().equals(spot.neighborhood().getId());
        int used = rechecks(idea);
        if (changed && used >= MAX_RECHECKS) {
            throw new BadRequestException("You've rechecked this idea " + MAX_RECHECKS + " times. Start a new idea instead");
        }

        Idea before = Idea.builder()
                .title(idea.getTitle()).description(idea.getDescription()).category(idea.getCategory())
                .latitude(idea.getLatitude()).longitude(idea.getLongitude()).neighborhood(idea.getNeighborhood())
                .status(idea.getStatus()).rejectionReason(idea.getRejectionReason())
                .build();
        idea.setTitle(request.getTitle().trim());
        idea.setDescription(request.getDescription().trim());
        idea.setCategory(request.getCategory().trim());
        idea.setLatitude(spot.latitude());
        idea.setLongitude(spot.longitude());
        idea.setNeighborhood(spot.neighborhood());
        idea.setStatus(IdeaStatus.SCREENING);
        ideaRepository.save(idea);

        IdeaCheckResponse check;
        try {
            check = ideaCheckService.run(ideaId);
        } catch (RuntimeException ex) {
            idea.setTitle(before.getTitle());
            idea.setDescription(before.getDescription());
            idea.setCategory(before.getCategory());
            idea.setLatitude(before.getLatitude());
            idea.setLongitude(before.getLongitude());
            idea.setNeighborhood(before.getNeighborhood());
            idea.setStatus(before.getStatus());
            idea.setRejectionReason(before.getRejectionReason());
            ideaRepository.save(idea);
            throw ex;
        }

        // The old business model described the old text; a new one is written for the edited idea
        if (changed) {
            businessModelRepository.findByIdeaId(ideaId).ifPresent(businessModelRepository::delete);
        }
        Idea checked = getIdea(ideaId);
        checked.setRecheckCount(changed ? used + 1 : used);
        List<String> warnings = applyCheck(checked, check);
        return localized(buildDetail(getIdea(ideaId), user, null, null, warnings));
    }

    /**
     * The author publishes a draft that passed the check: it goes on the map and residents can vote.
     * A check older than CHECK_FRESH_HOURS must be run again first, because a similar idea may have opened since.
     * Its vote target is fixed now from the business model's capital (VoteTarget).
     * An investor's idea also gets its feasibility report now.
     */
    public IdeaDetailResponse publish(User currentUser, Integer ideaId) {
        User user = getUser(currentUser.getId());
        Idea idea = ownIdea(user, ideaId);
        IdeaCheck check = ideaCheckRepository.findByIdeaId(ideaId)
                .filter(c -> ScreeningDecision.FIT.name().equals(c.getDecision()))
                .orElse(null);
        if (idea.getStatus() != IdeaStatus.DRAFT || check == null) {
            throw new BadRequestException("Only a draft that passed the check can be published");
        }
        if (isStale(check)) {
            throw new BadRequestException("This check is more than a day old. Check the idea again before publishing");
        }

        idea.setStatus(IdeaStatus.PUBLISHED);
        idea.setPublishedAt(LocalDateTime.now());
        ideaRepository.save(idea);

        List<String> warnings = new ArrayList<>();
        if (!businessModelRepository.existsByIdeaId(ideaId)) {
            try {
                businessModelService.generate(ideaId);
            } catch (RuntimeException ex) {
                warnings.add("The business model couldn't be generated right now: " + ex.getMessage());
            }
        }
        setVoteTarget(getIdea(ideaId));
        if (user.getRole() == Role.INVESTOR && feasibilityReportRepository.findByIdeaId(ideaId).isEmpty()) {
            try {
                feasibilityReportService.generate(ideaId);
            } catch (RuntimeException ex) {
                warnings.add("The feasibility report couldn't be generated right now: " + ex.getMessage());
            }
        }
        return localized(buildDetail(getIdea(ideaId), user, null, null, warnings));
    }

    /** The author drops a draft (or an idea that didn't pass): the idea and everything made for it are deleted. */
    @Transactional
    public void cancelDraft(User currentUser, Integer ideaId) {
        User user = getUser(currentUser.getId());
        Idea idea = ownIdea(user, ideaId);
        if (idea.getStatus() != IdeaStatus.DRAFT && idea.getStatus() != IdeaStatus.REJECTED) {
            throw new BadRequestException("Only drafts and ideas that didn't pass the check can be deleted");
        }
        // Drafts are never shown to anyone, so there are no votes, payments or contracts to keep
        businessModelRepository.findByIdeaId(ideaId).ifPresent(businessModelRepository::delete);
        feasibilityReportRepository.findByIdeaId(ideaId).ifPresent(feasibilityReportRepository::delete);
        ideaCheckRepository.findByIdeaId(ideaId).ifPresent(ideaCheckRepository::delete);
        nearbyPlaceRepository.deleteAllForIdea(ideaId);
        foundingMemberRepository.deleteByIdeaId(ideaId);
        ideaRepository.delete(idea);
    }

    /** Founder or investor asks again for the business model / report when the first try failed. */
    public IdeaDetailResponse retryAi(User currentUser, Integer ideaId) {
        User user = getUser(currentUser.getId());
        Idea idea = ownIdea(user, ideaId);
        if (idea.getStatus() == IdeaStatus.SCREENING || idea.getStatus() == IdeaStatus.REJECTED) {
            throw new BadRequestException("This idea isn't published");
        }
        List<String> warnings = new ArrayList<>();
        if (!businessModelRepository.existsByIdeaId(ideaId)) {
            try {
                businessModelService.generate(ideaId);
                // Published without a capital estimate (fallback target): set the real one while nobody has voted yet
                if (idea.getStatus() == IdeaStatus.PUBLISHED && voteRepository.countByIdeaId(ideaId) == 0) {
                    setVoteTarget(getIdea(ideaId));
                }
            } catch (RuntimeException ex) {
                warnings.add("The business model couldn't be generated right now: " + ex.getMessage());
            }
        }
        // The investor's report is written when the idea is published
        if (user.getRole() == Role.INVESTOR && idea.getStatus() != IdeaStatus.DRAFT
                && feasibilityReportRepository.findByIdeaId(ideaId).isEmpty()) {
            try {
                feasibilityReportService.generate(ideaId);
            } catch (RuntimeException ex) {
                warnings.add("The feasibility report couldn't be generated right now: " + ex.getMessage());
            }
        }
        return localized(buildDetail(getIdea(ideaId), user, null, null, warnings));
    }

    /** The idea's vote target from its business model's capital, or VoteTarget.FALLBACK without one. */
    private void setVoteTarget(Idea idea) {
        int target = businessModelRepository.findByIdeaId(idea.getId())
                .map(model -> VoteTarget.forCapital(model.getEstimatedCapitalMin(), model.getEstimatedCapitalMax()))
                .orElse(VoteTarget.FALLBACK);
        idea.setMembershipThreshold(target);
        ideaRepository.save(idea);
    }

    /** FIT → private draft with its business model draft; SIMILAR / SATURATED → rejected with the reason. */
    private List<String> applyCheck(Idea idea, IdeaCheckResponse check) {
        List<String> warnings = new ArrayList<>();
        if (Boolean.TRUE.equals(check.getCompetitorsUnavailable())) {
            warnings.add("The map service didn't answer, so nearby competitors weren't counted for this check");
        }
        if (ScreeningDecision.FIT.name().equals(check.getDecision())) {
            idea.setStatus(IdeaStatus.DRAFT);
            idea.setRejectionReason(null);
            ideaRepository.save(idea);
            // Written now so the author can read it on the review screen before publishing
            if (!businessModelRepository.existsByIdeaId(idea.getId())) {
                try {
                    businessModelService.generate(idea.getId());
                } catch (RuntimeException ex) {
                    warnings.add("The business model couldn't be generated right now: " + ex.getMessage());
                }
            }
            // The review screen shows the target the idea will have; publishing sets it again
            setVoteTarget(idea);
        } else {
            idea.setStatus(IdeaStatus.REJECTED);
            idea.setRejectionReason(rejectionReason(check));
            ideaRepository.save(idea);
        }
        return warnings;
    }

    /** Where a new or edited idea goes, and its neighborhood. Residents stay near one of their saved homes. */
    private Spot placeOf(User user, ProposeIdeaRequest request) {
        Double latitude = request.getLatitude() != null ? request.getLatitude() : user.getLatitude();
        Double longitude = request.getLongitude() != null ? request.getLongitude() : user.getLongitude();
        if ((latitude == null) != (longitude == null)) {
            throw new BadRequestException("Send both latitude and longitude, or neither");
        }
        if (user.getRole() == Role.RESIDENT) {
            // A resident's idea goes near one of their saved addresses (the spot belongs to the idea, not the voter)
            List<Address> addresses = addressService.addressesOf(user);
            if (latitude == null && !addresses.isEmpty()) {
                latitude = addresses.get(0).getLatitude();
                longitude = addresses.get(0).getLongitude();
            }
            double[] spot = latitude != null ? new double[]{latitude, longitude} : null;
            if (spot != null && !addresses.isEmpty()
                    && addresses.stream().noneMatch(a -> AddressService.distanceKm(a, spot) <= RESIDENT_PROPOSE_RADIUS_KM)) {
                throw new BadRequestException("Residents can propose ideas within " + (int) RESIDENT_PROPOSE_RADIUS_KM
                        + " km of their home. Move the pin closer to your home");
            }
        }
        return new Spot(latitude, longitude, resolveNeighborhood(user, request.getNeighborhoodId(), latitude, longitude));
    }

    private record Spot(Double latitude, Double longitude, Neighborhood neighborhood) {
    }

    private Idea ownIdea(User user, Integer ideaId) {
        Idea idea = getIdea(ideaId);
        if (!idea.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the idea's creator can do this");
        }
        return idea;
    }

    private static int rechecks(Idea idea) {
        return idea.getRecheckCount() != null ? idea.getRecheckCount() : 0;
    }

    private static boolean isStale(IdeaCheck check) {
        return check.getCheckedAt() != null
                && check.getCheckedAt().isBefore(LocalDateTime.now().minusHours(CHECK_FRESH_HOURS));
    }

    // ---------- Vote ----------

    /**
     * A resident votes for a published idea inside their voting radius.
     * When the votes reach the idea's threshold the request is COMPLETED and investors pay the full price
     * (before that they can buy it early, at a lower price).
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

        // Eligibility comes from the saved addresses only; coordinates in the request are ignored
        List<Address> addresses = addressService.addressesOf(user);
        if (addresses.isEmpty()) {
            throw new BadRequestException("Add your home address in your account to vote");
        }
        if (addresses.stream().anyMatch(a -> Boolean.TRUE.equals(a.getNeedsConfirmation()))) {
            throw new BadRequestException("Confirm your home address in your account to vote");
        }
        double[] point = LocationService.pointOf(idea);
        if (point == null) {
            throw new BadRequestException("This idea has no location yet");
        }
        Address from = eligibleAddress(addresses, idea);
        if (from == null) {
            double nearestKm = AddressService.distanceKm(AddressService.nearest(addresses, point), point);
            throw new BadRequestException("You can vote only for ideas within " + VOTING_RADIUS_KM
                    + " km of your saved addresses. This one is " + LocationService.round2(nearestKm) + " km away");
        }

        voteRepository.save(Vote.builder()
                .user(user)
                .idea(idea)
                .address(from)
                .distanceKm(LocationService.round2(AddressService.distanceKm(from, point)))
                .build());

        long votes = voteRepository.countByIdeaId(ideaId);
        if (votes >= threshold(idea)) {
            idea.setStatus(IdeaStatus.COMPLETED);
            idea.setConfirmedAt(LocalDateTime.now());
            ideaRepository.save(idea);
            // n8n generates the alerts: investors interested in the neighborhood, and the founder
            n8nService.sendEvent("IDEA_COMPLETED", completedEventData(idea, votes));
            notificationService.notify(idea.getOwner(), NotificationService.VOTE_TARGET_REACHED, idea, null,
                    "idea.html?id=" + idea.getId());
        }
        return localized(buildDetail(idea, user, null, null, List.of()));
    }

    /**
     * AI text in the reader's language (Accept-Language). AI text is written once in the author's language;
     * a reader of the other language gets a cached translation (AiTranslationService). Used on single-idea
     * responses only, so lists never wait for translations.
     */
    private IdeaDetailResponse localized(IdeaDetailResponse detail) {
        if (detail.getFeasibilitySummary() != null) {
            String summary = aiTranslationService.toViewer(
                    AiTranslationService.CHECK, detail.getId(), detail.getFeasibilitySummary());
            if (summary != null && !summary.isBlank()) {
                detail.setFeasibilitySummary(summary);
            }
        }
        BusinessModelResponse model = detail.getBusinessModel();
        if (model != null) {
            Map<String, String> text = aiTranslationService.toViewer(AiTranslationService.BUSINESS_MODEL, model.getId(),
                    textFields("valueProposition", model.getValueProposition(), "targetCustomers", model.getTargetCustomers(),
                            "revenueStreams", model.getRevenueStreams(), "costStructure", model.getCostStructure(),
                            "channels", model.getChannels(), "risks", model.getRisks()));
            if (text == null) {
                return detail;
            }
            model.setValueProposition(text.get("valueProposition"));
            model.setTargetCustomers(text.get("targetCustomers"));
            model.setRevenueStreams(text.get("revenueStreams"));
            model.setCostStructure(text.get("costStructure"));
            model.setChannels(text.get("channels"));
            model.setRisks(text.get("risks"));
        }
        FeasibilityReportResponse report = detail.getFeasibilityReport();
        if (report != null) {
            Map<String, String> text = aiTranslationService.toViewer(AiTranslationService.FEASIBILITY_REPORT, report.getId(),
                    textFields("recommendation", report.getRecommendation(), "risks", report.getRisks(),
                            "peakDemandTime", report.getPeakDemandTime()));
            if (text == null) {
                return detail;
            }
            report.setRecommendation(text.get("recommendation"));
            report.setRisks(text.get("risks"));
            report.setPeakDemandTime(text.get("peakDemandTime"));
        }
        return detail;
    }

    // Map.of doesn't take nulls; empty fields are left out of the translation and come back as they were
    private static Map<String, String> textFields(String... keysAndValues) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
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

    /**
     * The idea page for one viewer:
     * - residents see the idea and their vote, never the vote counts or the business model
     * - the founder and admins see everything
     * - investors see the idea in full (the investor experience); the full feasibility report opens after unlocking.
     *   The confidential summary (teaser) fields are kept in the response for the matching and AI search lists
     */
    private IdeaDetailResponse buildDetail(Idea idea, User viewer, Double latitude, Double longitude,
                                           List<String> warnings) {
        boolean founder = idea.getOwner().getId().equals(viewer.getId());
        Role role = viewer.getRole();
        boolean investor = role == Role.INVESTOR;
        boolean admin = role == Role.ADMIN;
        // Only live contracts count: an expired or refunded adoption gives the idea back to the investors
        boolean unlocked = investor && contractRepository.existsByIdeaIdAndInvestorIdAndStatusNot(
                idea.getId(), viewer.getId(), ContractStatus.TERMINATED);
        boolean teaser = false;
        boolean seesNumbers = founder || investor || admin;
        boolean seesModel = founder || admin || investor;

        double[] point = teaser ? LocationService.approximatePointOf(idea) : LocationService.pointOf(idea);
        Neighborhood neighborhood = idea.getNeighborhood();
        var stage = projectService.stageOfIdea(idea).orElse(null);

        IdeaDetailResponse.IdeaDetailResponseBuilder out = IdeaDetailResponse.builder()
                .id(idea.getId())
                .title(teaser ? null : idea.getTitle())
                .description(teaser ? null : idea.getDescription())
                .category(idea.getCategory())
                .status(idea.getStatus().name())
                .neighborhoodId(neighborhood.getId())
                .neighborhoodName(neighborhood.getName())
                .city(neighborhood.getCity())
                .latitude(point != null ? point[0] : null)
                .longitude(point != null ? point[1] : null)
                .locationLabel(teaser ? null : LocationService.sideOf(idea))
                .publishedAt(idea.getPublishedAt())
                .confirmedAt(idea.getConfirmedAt())
                .stage(stage != null ? stage.stage() : null)
                .stageIndex(stage != null ? stage.index() : null)
                .stageCount(stage != null ? stage.count() : null)
                .founder(founder)
                .viewerRole(founder ? "FOUNDER" : (admin ? "ADMIN" : (investor ? "INVESTOR" : "VOTER")))
                .rejectionReason(founder || admin ? idea.getRejectionReason() : null)
                .rechecksLeft(founder ? Math.max(0, MAX_RECHECKS - rechecks(idea)) : null)
                .warnings(warnings.isEmpty() ? null : warnings);

        // Voting: everyone sees whether they are in range; only residents can vote
        if (role == Role.RESIDENT) {
            boolean hasVoted = voteRepository.existsByUserIdAndIdeaId(viewer.getId(), idea.getId());
            List<Address> addresses = addressService.addressesOf(viewer);
            boolean confirmed = addresses.stream().noneMatch(a -> Boolean.TRUE.equals(a.getNeedsConfirmation()));
            Double km = null;
            boolean inRange = false;
            Address from = null;
            if (point != null && !addresses.isEmpty()) {
                Address eligible = eligibleAddress(addresses, idea);
                from = eligible != null ? eligible : AddressService.nearest(addresses, point);
                km = LocationService.round2(AddressService.distanceKm(from, point));
                inRange = eligible != null;
            }
            out.votingRadiusKm(VOTING_RADIUS_KM)
                    .distanceKm(km)
                    .inRange(inRange)
                    .voted(hasVoted)
                    .voteAddressId(from != null ? from.getId() : null)
                    .voteAddressLabel(from != null ? from.getLabel() : null)
                    .addressNeedsConfirmation(!confirmed)
                    .hasAddress(!addresses.isEmpty())
                    .canVote(!founder && confirmed && inRange && !hasVoted && idea.getStatus() == IdeaStatus.PUBLISHED)
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
        }
        if (seesModel && businessModelRepository.existsByIdeaId(idea.getId())) {
            out.businessModel(businessModelService.getByIdea(idea.getId()));
        }

        // AI check: the summary and competitor names are part of the idea; investors get only the numbers before unlocking
        ideaCheckRepository.findByIdeaId(idea.getId()).ifPresent((IdeaCheck check) -> {
            out.checkDecision(check.getDecision())
                    .checkScore(check.getScore())
                    .shortageScore(check.getShortageScore())
                    .competitionScore(check.getCompetitionScore())
                    .demandScore(check.getDemandScore())
                    .checkedAt(founder || admin ? check.getCheckedAt() : null)
                    .scarcityFound(check.getScarcityFound())
                    .searchRadiusKm(check.getSearchRadiusKm())
                    .competitorsCount(check.getCompetitorsCount())
                    .nearestCompetitorKm(check.getNearestCompetitorKm());
            if (founder && idea.getStatus() == IdeaStatus.DRAFT) {
                boolean fit = ScreeningDecision.FIT.name().equals(check.getDecision());
                out.checkStale(isStale(check))
                        .canPublish(fit && !isStale(check));
            }
            if (!teaser) {
                out.feasibilitySummary(check.getFeasibilitySummary())
                        .competitors(nearbyPlaceService.getByIdea(idea.getId()));
                if (check.getSimilarIdea() != null) {
                    out.similarIdeaId(check.getSimilarIdea().getId())
                            .similarIdeaTitle(check.getSimilarIdea().getTitle());
                }
            }
        });

        if (investor) {
            InvestorStatus status = viewer.getInvestorStatus() != null ? viewer.getInvestorStatus() : InvestorStatus.PENDING;
            boolean adopted = contractRepository.existsByIdeaIdAndStatusNot(idea.getId(), ContractStatus.TERMINATED);
            boolean heldBefore = !unlocked
                    && opportunityAccessRepository.existsByInvestorIdAndIdeaId(viewer.getId(), idea.getId());
            boolean openForOwner = idea.getStatus() == IdeaStatus.PUBLISHED || idea.getStatus() == IdeaStatus.COMPLETED;
            out.investorStatus(status)
                    .unlocked(unlocked)
                    .adopted(adopted)
                    .teaser(teaser)
                    .approxRadiusM(teaser ? LocationService.APPROX_RADIUS_M : null)
                    .votesLast7Days(voteRepository.countByIdeaIdAndCreatedAtAfter(idea.getId(), LocalDateTime.now().minusDays(7)));
            businessModelRepository.findByIdeaId(idea.getId()).ifPresent(model -> out
                    .capitalMin(model.getEstimatedCapitalMin())
                    .capitalMax(model.getEstimatedCapitalMax()));
            feasibilityReportRepository.findByIdeaId(idea.getId()).ifPresent(report -> {
                out.gapScore(report.getGapScore());
                // The full report: after unlocking, or for the investor who proposed the idea
                if (unlocked) {
                    out.feasibilityReport(feasibilityReportService.getFull(idea.getId(), viewer.getId()));
                } else if (founder) {
                    out.feasibilityReport(feasibilityReportService.getByIdea(idea.getId()));
                }
            });

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
            } else if (!founder && !openForOwner) {
                // Open ideas can be bought before they reach the vote target, at the lower early price
                blocked = "This idea isn't open for investors";
            }
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
            var nearest = locationService.resolveNeighborhood(latitude, longitude);
            if (nearest.isPresent()) {
                return nearest.get();
            }
        }
        if (user.getNeighborhood() != null) {
            return user.getNeighborhood();
        }
        throw new BadRequestException("Choose the neighborhood for this idea");
    }

    // The address a resident votes from for this idea: the primary if it is in range, else the nearest one in range
    private static Address eligibleAddress(List<Address> addresses, Idea idea) {
        double[] point = LocationService.pointOf(idea);
        if (point == null) {
            return null;
        }
        return addresses.stream()
                .filter(a -> AddressService.distanceKm(a, point) <= VOTING_RADIUS_KM)
                .min(Comparator.comparing((Address a) -> !Boolean.TRUE.equals(a.getPrimaryAddress()))
                        .thenComparingDouble(a -> AddressService.distanceKm(a, point)))
                .orElse(null);
    }

    // The resident map's center: the chosen saved address, else the primary one
    private static double[] residentOrigin(List<Address> addresses, Integer addressId) {
        Address chosen = addresses.stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .orElse(addresses.isEmpty() ? null : addresses.get(0));
        if (chosen == null) {
            throw new BadRequestException("Add your home address in your account to see ideas around you");
        }
        return new double[]{chosen.getLatitude(), chosen.getLongitude()};
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

    private static String shorten(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String clean = text.trim().replaceAll("\\s+", " ");
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength - 1).trim() + "…";
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
                ? idea.getMembershipThreshold() : VoteTarget.FALLBACK;
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
