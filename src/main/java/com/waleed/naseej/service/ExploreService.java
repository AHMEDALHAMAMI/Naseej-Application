package com.waleed.naseej.service;

import com.waleed.naseej.dto.out.CityStatResponse;
import com.waleed.naseej.dto.out.NeighborhoodStatResponse;
import com.waleed.naseej.dto.out.OpportunityResponse;
import com.waleed.naseej.entity.BusinessModel;
import com.waleed.naseej.entity.FeasibilityReport;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.BusinessModelRepository;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.FeasibilityReportRepository;
import com.waleed.naseej.repository.IdeaCheckRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Investor map: Saudi Arabia → cities with their open opportunities → neighborhoods → the ideas in one neighborhood.
 * An opportunity is an idea that is published (collecting votes) or completed (ready to unlock).
 * Also the investor search (name, city, category, capital) and the requests rising fastest.
 */
@Service
@RequiredArgsConstructor
public class ExploreService {

    // Every published opportunity, including the ones already adopted or opened, so the map shows the whole picture
    private static final List<IdeaStatus> OPEN_STATUSES = List.of(
            IdeaStatus.PUBLISHED, IdeaStatus.COMPLETED, IdeaStatus.ADOPTED, IdeaStatus.OPENED);

    // Rising requests are the ones an investor can still act on
    private static final List<IdeaStatus> AVAILABLE_STATUSES = List.of(IdeaStatus.PUBLISHED, IdeaStatus.COMPLETED);

    private static final int MAX_SEARCH_RESULTS = 100;
    private static final int MAX_RISING_RESULTS = 50;
    private static final int MAX_RISING_DAYS = 90;

    private final IdeaRepository ideaRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final VoteRepository voteRepository;
    private final FeasibilityReportRepository feasibilityReportRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final IdeaCheckRepository ideaCheckRepository;
    private final BusinessModelRepository businessModelRepository;
    private final ProjectService projectService;

    public List<CityStatResponse> cities() {
        Map<String, List<Idea>> byCity = ideaRepository.findByStatusIn(OPEN_STATUSES).stream()
                .collect(Collectors.groupingBy(idea -> idea.getNeighborhood().getCity().trim(),
                        LinkedHashMap::new, Collectors.toList()));

        // City center = average of its neighborhoods that have coordinates
        Map<String, List<Neighborhood>> neighborhoodsByCity = neighborhoodRepository.findAll().stream()
                .filter(n -> n.getLatitude() != null && n.getLongitude() != null)
                .collect(Collectors.groupingBy(n -> n.getCity().trim()));

        return byCity.entrySet().stream().map(entry -> {
            List<Neighborhood> located = neighborhoodsByCity.getOrDefault(entry.getKey(), List.of());
            Double lat = located.isEmpty() ? null
                    : located.stream().mapToDouble(Neighborhood::getLatitude).average().orElse(0);
            Double lng = located.isEmpty() ? null
                    : located.stream().mapToDouble(Neighborhood::getLongitude).average().orElse(0);
            long hoods = entry.getValue().stream().map(i -> i.getNeighborhood().getId()).distinct().count();
            return CityStatResponse.builder()
                    .city(entry.getKey())
                    .latitude(lat)
                    .longitude(lng)
                    .opportunities((long) entry.getValue().size())
                    .neighborhoods((int) hoods)
                    .build();
        }).sorted(Comparator.comparing(CityStatResponse::getOpportunities).reversed()).toList();
    }

    public List<NeighborhoodStatResponse> neighborhoods(String city) {
        String wanted = city.trim();
        List<Idea> ideas = ideaRepository.findByStatusIn(OPEN_STATUSES).stream()
                .filter(idea -> idea.getNeighborhood().getCity().trim().equalsIgnoreCase(wanted))
                .toList();
        Map<Integer, Neighborhood> neighborhoods = new HashMap<>();
        ideas.forEach(idea -> neighborhoods.putIfAbsent(idea.getNeighborhood().getId(), idea.getNeighborhood()));
        Map<Integer, Long> counts = ideas.stream()
                .collect(Collectors.groupingBy(idea -> idea.getNeighborhood().getId(), Collectors.counting()));

        return counts.entrySet().stream().map(entry -> {
                    Neighborhood n = neighborhoods.get(entry.getKey());
                    return NeighborhoodStatResponse.builder()
                            .neighborhoodId(n.getId())
                            .name(n.getName())
                            .city(n.getCity())
                            .latitude(n.getLatitude())
                            .longitude(n.getLongitude())
                            .opportunities(entry.getValue())
                            .build();
                })
                .sorted(Comparator.comparing(NeighborhoodStatResponse::getOpportunities).reversed())
                .toList();
    }

    /** Ideas in one neighborhood with their side of the neighborhood and vote %, highest first. */
    public List<OpportunityResponse> opportunities(User currentUser, Integer neighborhoodId) {
        if (!neighborhoodRepository.existsById(neighborhoodId)) {
            throw new ResourceNotFoundException("Neighborhood not found with id: " + neighborhoodId);
        }
        User investor = getUser(currentUser.getId());

        List<Idea> ideas = ideaRepository.findByNeighborhoodIdAndStatusIn(neighborhoodId, OPEN_STATUSES);
        if (ideas.isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> votes = voteCounts(ideas);
        // With the place and the estimated capital, so the map's budget filter works here too
        Map<Integer, BusinessModel> models = businessModels(ideas.stream().map(Idea::getId).toList());

        return ideas.stream()
                .map(idea -> withPlaceAndCapital(
                        toOpportunity(idea, investor, votes.getOrDefault(idea.getId(), 0L)), idea, models.get(idea.getId()))
                        .build())
                .sorted(availableFirst())
                .toList();
    }

    // ---------- Search ----------

    /**
     * Investor search across every neighborhood: words in the title, description, category or neighborhood name,
     * a city, a category and the most capital the investor wants to put in (from the AI business model).
     * Available opportunities first, then the ones closest to their vote target.
     */
    public List<OpportunityResponse> search(User currentUser, String q, String city, String category,
                                            BigDecimal maxCapital, Integer limit) {
        if (maxCapital != null && maxCapital.signum() < 0) {
            throw new BadRequestException("Capital can't be negative");
        }
        int max = limit == null ? 50 : Math.max(1, Math.min(limit, MAX_SEARCH_RESULTS));
        String words = normalize(q);
        String wantedCity = normalize(city);
        String wantedCategory = normalize(category);
        User investor = getUser(currentUser.getId());

        List<Idea> ideas = ideaRepository.findByStatusIn(OPEN_STATUSES).stream()
                .filter(idea -> words == null || contains(idea.getTitle(), words) || contains(idea.getDescription(), words)
                        || contains(idea.getCategory(), words) || contains(idea.getNeighborhood().getName(), words))
                .filter(idea -> wantedCity == null || wantedCity.equals(normalize(idea.getNeighborhood().getCity())))
                .filter(idea -> wantedCategory == null || contains(idea.getCategory(), wantedCategory))
                .toList();
        if (ideas.isEmpty()) {
            return List.of();
        }

        Map<Integer, BusinessModel> models = businessModels(ideas.stream().map(Idea::getId).toList());
        if (maxCapital != null) {
            // Only ideas whose business model says they can start with this budget
            ideas = ideas.stream()
                    .filter(idea -> {
                        BusinessModel model = models.get(idea.getId());
                        return model != null && model.getEstimatedCapitalMin() != null
                                && model.getEstimatedCapitalMin().compareTo(maxCapital) <= 0;
                    })
                    .toList();
            if (ideas.isEmpty()) {
                return List.of();
            }
        }
        Map<Integer, Long> votes = voteCounts(ideas);

        return ideas.stream()
                .map(idea -> withPlaceAndCapital(
                        toOpportunity(idea, investor, votes.getOrDefault(idea.getId(), 0L)), idea, models.get(idea.getId()))
                        .build())
                .sorted(availableFirst())
                .limit(max)
                .toList();
    }

    // ---------- Rising ----------

    /**
     * Requests that gained the most votes in the last days (default 7), for the investor page and the weekly
     * digest in n8n. growthPercent compares the new votes with the votes the idea had before them; it is empty
     * for an idea whose votes are all new.
     */
    public List<OpportunityResponse> rising(User currentUser, Integer days, Integer limit) {
        int period = days == null ? 7 : days;
        if (period < 1 || period > MAX_RISING_DAYS) {
            throw new BadRequestException("Days must be between 1 and " + MAX_RISING_DAYS);
        }
        int max = limit == null ? 10 : Math.max(1, Math.min(limit, MAX_RISING_RESULTS));
        User investor = getUser(currentUser.getId());

        Map<Integer, Long> recent = new HashMap<>();
        voteRepository.countSince(LocalDateTime.now().minusDays(period))
                .forEach(row -> recent.put((Integer) row[0], (Long) row[1]));
        if (recent.isEmpty()) {
            return List.of();
        }

        List<Idea> ideas = ideaRepository.findByStatusIn(AVAILABLE_STATUSES).stream()
                .filter(idea -> recent.containsKey(idea.getId()))
                .toList();
        if (ideas.isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> totals = voteCounts(ideas);
        Map<Integer, BusinessModel> models = businessModels(ideas.stream().map(Idea::getId).toList());

        return ideas.stream()
                .map(idea -> {
                    long total = totals.getOrDefault(idea.getId(), 0L);
                    long gained = recent.getOrDefault(idea.getId(), 0L);
                    long before = total - gained;
                    return withPlaceAndCapital(toOpportunity(idea, investor, total), idea, models.get(idea.getId()))
                            .recentVotes(gained)
                            .growthPercent(before > 0 ? (int) Math.round(gained * 100.0 / before) : null)
                            .build();
                })
                .sorted(Comparator.comparing(OpportunityResponse::getRecentVotes).reversed()
                        .thenComparing(Comparator.comparing(OpportunityResponse::getVotePercent).reversed()))
                .limit(max)
                .toList();
    }

    // ---------- Shared ----------

    private OpportunityResponse.OpportunityResponseBuilder toOpportunity(Idea idea, User investor, long votes) {
        boolean verified = investor.getInvestorStatus() == InvestorStatus.VERIFIED;
        int threshold = DiscoverService.threshold(idea);
        double[] point = LocationService.pointOf(idea);
        // Only a live contract counts as unlocked; an expired or refunded one doesn't
        boolean unlocked = contractRepository.existsByIdeaIdAndInvestorIdAndStatusNot(
                idea.getId(), investor.getId(), ContractStatus.TERMINATED);
        boolean mine = Objects.equals(idea.getOwner().getId(), investor.getId());
        // Early access: open ideas can be bought before they reach the vote target
        boolean open = idea.getStatus() == IdeaStatus.PUBLISHED || idea.getStatus() == IdeaStatus.COMPLETED;
        boolean canUnlock = verified && !mine && !unlocked && open
                && !opportunityAccessRepository.existsByInvestorIdAndIdeaId(investor.getId(), idea.getId());
        return OpportunityResponse.builder()
                .id(idea.getId())
                .title(idea.getTitle())
                .category(idea.getCategory())
                .status(idea.getStatus().name())
                .locationLabel(LocationService.sideOf(idea))
                .latitude(point != null ? point[0] : null)
                .longitude(point != null ? point[1] : null)
                .votesCount(votes)
                .membershipThreshold(threshold)
                .votePercent(DiscoverService.percent(votes, threshold))
                .gapScore(feasibilityReportRepository.findByIdeaId(idea.getId())
                        .map(FeasibilityReport::getGapScore).orElse(null))
                .unlocked(unlocked)
                .canUnlock(canUnlock);
    }

    private static OpportunityResponse.OpportunityResponseBuilder withPlaceAndCapital(
            OpportunityResponse.OpportunityResponseBuilder builder, Idea idea, BusinessModel model) {
        return builder
                .neighborhoodId(idea.getNeighborhood().getId())
                .neighborhoodName(idea.getNeighborhood().getName())
                .city(idea.getNeighborhood().getCity())
                .estimatedCapitalMin(model != null ? model.getEstimatedCapitalMin() : null)
                .estimatedCapitalMax(model != null ? model.getEstimatedCapitalMax() : null);
    }

    // Available opportunities come first, adopted or opened ones after them, then by vote %
    private static Comparator<OpportunityResponse> availableFirst() {
        return Comparator.comparing((OpportunityResponse o) -> isTaken(o.getStatus()))
                .thenComparing(Comparator.comparing(OpportunityResponse::getVotePercent).reversed());
    }

    // ---------- Matches by budget and area, top list and AI search candidates (confidential summaries) ----------

    /**
     * Every opportunity an investor can still unlock (published or target reached, not adopted), across all cities,
     * as the same confidential summaries the map shows. Used by the top list and the AI search.
     */
    public List<OpportunityResponse> openOpportunities(User currentUser) {
        User investor = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUser.getId()));
        boolean verified = investor.getInvestorStatus() == InvestorStatus.VERIFIED;
        List<Idea> ideas = ideaRepository.findByStatusIn(List.of(IdeaStatus.PUBLISHED, IdeaStatus.COMPLETED)).stream()
                .filter(idea -> !opportunityAccessRepository.existsByIdeaId(idea.getId()))
                .toList();
        if (ideas.isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> votes = new HashMap<>();
        voteRepository.countByIdeaIds(ideas.stream().map(Idea::getId).toList())
                .forEach(row -> votes.put((Integer) row[0], (Long) row[1]));
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        return ideas.stream()
                .map(idea -> toOpportunity(idea, investor, verified, votes.getOrDefault(idea.getId(), 0L), weekAgo))
                .toList();
    }

    public Optional<String> neighborhoodName(Integer id) {
        return neighborhoodRepository.findById(id).map(n -> n.getName() + ", " + n.getCity());
    }

    // Opportunities this close to the chosen neighborhood also count as "near you"
    private static final double NEARBY_KM = 6.0;

    // Budget fit labels (OpportunityResponse.budgetFit)
    public static final String FIT_FULL = "FULL";
    public static final String FIT_PARTIAL = "PARTIAL";
    public static final String FIT_UNKNOWN = "UNKNOWN";
    static final String FIT_NONE = "NONE";

    /**
     * How an opportunity's estimated capital [cMin, cMax] sits in the investor's range [bMin, bMax]
     * (a null bMax means "and above"): FULL when it is inside, PARTIAL when the ranges overlap, UNKNOWN when the
     * capital isn't estimated yet, NONE when they don't overlap. Null when no budget was given.
     */
    public static String budgetFit(BigDecimal cMin, BigDecimal cMax, BigDecimal bMin, BigDecimal bMax) {
        if (bMin == null && bMax == null) {
            return null;
        }
        if (cMin == null && cMax == null) {
            return FIT_UNKNOWN;
        }
        BigDecimal lo = cMin != null ? cMin : cMax;
        BigDecimal hi = cMax != null ? cMax : cMin;
        BigDecimal from = bMin != null ? bMin : BigDecimal.ZERO;
        boolean startsInside = lo.compareTo(from) >= 0;
        boolean endsInside = bMax == null || hi.compareTo(bMax) <= 0;
        if (startsInside && endsInside) {
            return FIT_FULL;
        }
        boolean overlaps = hi.compareTo(from) >= 0 && (bMax == null || lo.compareTo(bMax) <= 0);
        return overlaps ? FIT_PARTIAL : FIT_NONE;
    }

    static int fitRank(String fit) {
        if (fit == null || FIT_FULL.equals(fit)) {
            return 0;
        }
        return FIT_PARTIAL.equals(fit) ? 1 : 2;
    }

    /**
     * Opportunities that fit the investor's search before they type anything: a city, a neighborhood
     * (its own opportunities first, then the ones in nearby neighborhoods with their distance) and a budget range
     * (opportunities whose estimated capital is inside the range first, then partial overlaps, then the ones
     * without an estimate; no overlap = left out). Every filter is optional, and none of them changes the profile.
     */
    public List<OpportunityResponse> match(User currentUser, String city, Integer neighborhoodId, BigDecimal budgetMin,
                                           BigDecimal budgetMax, int limit) {
        if (budgetMin != null && budgetMax != null && budgetMin.compareTo(budgetMax) > 0) {
            throw new BadRequestException("The minimum budget must be at or below the maximum");
        }
        Neighborhood home = neighborhoodId == null ? null : neighborhoodRepository.findById(neighborhoodId)
                .orElseThrow(() -> new ResourceNotFoundException("Neighborhood not found with id: " + neighborhoodId));
        String wantedCity = home != null ? home.getCity() : (city == null || city.isBlank() ? null : city.trim());
        boolean located = home != null && home.getLatitude() != null && home.getLongitude() != null;

        return openOpportunities(currentUser).stream()
                .peek(o -> o.setBudgetFit(budgetFit(o.getCapitalMin(), o.getCapitalMax(), budgetMin, budgetMax)))
                .filter(o -> !FIT_NONE.equals(o.getBudgetFit()))
                .peek(o -> {
                    if (located && o.getLatitude() != null) {
                        o.setDistanceKm(LocationService.round2(LocationService.distanceKm(
                                home.getLatitude(), home.getLongitude(), o.getLatitude(), o.getLongitude())));
                    }
                })
                .filter(o -> {
                    if (home != null) {
                        return home.getId().equals(o.getNeighborhoodId())
                                || (o.getDistanceKm() != null && o.getDistanceKm() <= NEARBY_KM);
                    }
                    return wantedCity == null || wantedCity.equalsIgnoreCase(o.getCity());
                })
                .sorted(Comparator.comparingInt((OpportunityResponse o) -> fitRank(o.getBudgetFit()))
                        .thenComparing(o -> home == null || !home.getId().equals(o.getNeighborhoodId()))
                        .thenComparing(o -> o.getDistanceKm() == null ? 0 : o.getDistanceKm())
                        .thenComparing(Comparator.comparingDouble(ExploreService::strength).reversed()))
                .limit(Math.max(1, Math.min(80, limit)))
                .toList();
    }

    /** The strongest opportunities right now: closest to (or past) the vote target and gaining votes fastest. */
    public List<OpportunityResponse> top(User currentUser, int limit) {
        return openOpportunities(currentUser).stream()
                .sorted(Comparator.comparingDouble(ExploreService::strength).reversed())
                .limit(Math.max(1, Math.min(12, limit)))
                .toList();
    }

    static double strength(OpportunityResponse o) {
        return o.getVotePercent() + 4.0 * nz(o.getVotesLast7Days()) + 0.3 * nz(o.getCheckScore()) + 0.3 * nz(o.getGapScore());
    }

    private static double nz(Number n) {
        return n == null ? 0 : n.doubleValue();
    }

    private OpportunityResponse toOpportunity(Idea idea, User investor, boolean verified, long count, LocalDateTime weekAgo) {
        int threshold = DiscoverService.threshold(idea);
        boolean unlocked = opportunityAccessRepository.existsByInvestorIdAndIdeaId(investor.getId(), idea.getId());
        boolean mine = Objects.equals(idea.getOwner().getId(), investor.getId());
        boolean teaser = !unlocked && !mine;
        double[] point = teaser ? LocationService.approximatePointOf(idea) : LocationService.pointOf(idea);
        boolean open = idea.getStatus() == IdeaStatus.PUBLISHED || idea.getStatus() == IdeaStatus.COMPLETED;
        var check = ideaCheckRepository.findByIdeaId(idea.getId()).orElse(null);
        var model = businessModelRepository.findByIdeaId(idea.getId()).orElse(null);
        var stage = projectService.stageOfIdea(idea).orElse(null);
        return OpportunityResponse.builder()
                .id(idea.getId())
                .title(teaser ? null : idea.getTitle())
                .category(idea.getCategory())
                .status(idea.getStatus().name())
                .teaser(teaser)
                .approxRadiusM(teaser ? LocationService.APPROX_RADIUS_M : null)
                .neighborhoodId(idea.getNeighborhood().getId())
                .neighborhoodName(idea.getNeighborhood().getName())
                .city(idea.getNeighborhood().getCity())
                .locationLabel(teaser ? null : LocationService.sideOf(idea))
                .latitude(point != null ? point[0] : null)
                .longitude(point != null ? point[1] : null)
                .checkScore(check != null ? check.getScore() : null)
                .competitorsCount(check != null ? check.getCompetitorsCount() : null)
                .votesLast7Days(voteRepository.countByIdeaIdAndCreatedAtAfter(idea.getId(), weekAgo))
                .capitalMin(model != null ? model.getEstimatedCapitalMin() : null)
                .capitalMax(model != null ? model.getEstimatedCapitalMax() : null)
                .stage(stage != null ? stage.stage() : null)
                .stageIndex(stage != null ? stage.index() : null)
                .stageCount(stage != null ? stage.count() : null)
                .votesCount(count)
                .membershipThreshold(threshold)
                .votePercent(DiscoverService.percent(count, threshold))
                .gapScore(feasibilityReportRepository.findByIdeaId(idea.getId())
                        .map(FeasibilityReport::getGapScore).orElse(null))
                .unlocked(unlocked)
                // Early access: open ideas can be unlocked before they reach the vote target
                .canUnlock(verified && !mine && !unlocked && open)
                .build();
    }
    private static boolean isTaken(String status) {
        return IdeaStatus.ADOPTED.name().equals(status) || IdeaStatus.OPENED.name().equals(status);
    }

    private Map<Integer, Long> voteCounts(Collection<Idea> ideas) {
        Map<Integer, Long> votes = new HashMap<>();
        voteRepository.countByIdeaIds(ideas.stream().map(Idea::getId).toList())
                .forEach(row -> votes.put((Integer) row[0], (Long) row[1]));
        return votes;
    }

    private Map<Integer, BusinessModel> businessModels(Collection<Integer> ideaIds) {
        Map<Integer, BusinessModel> models = new HashMap<>();
        businessModelRepository.findByIdeaIdIn(ideaIds)
                .forEach(model -> models.put(model.getIdea().getId(), model));
        return models;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String text, String words) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(words);
    }

    private User getUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
