package com.waleed.naseej.service;

import com.waleed.naseej.dto.out.CityStatResponse;
import com.waleed.naseej.dto.out.NeighborhoodStatResponse;
import com.waleed.naseej.dto.out.OpportunityResponse;
import com.waleed.naseej.entity.FeasibilityReport;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.FeasibilityReportRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Investor map: Saudi Arabia → cities with their open opportunities → neighborhoods → the ideas in one neighborhood.
 * An opportunity is an idea that is published (collecting votes) or completed (ready to unlock).
 */
@Service
@RequiredArgsConstructor
public class ExploreService {

    // Every published opportunity, including the ones already adopted or opened, so the map shows the whole picture
    private static final List<IdeaStatus> OPEN_STATUSES = List.of(
            IdeaStatus.PUBLISHED, IdeaStatus.COMPLETED, IdeaStatus.ADOPTED, IdeaStatus.OPENED);

    private final IdeaRepository ideaRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final VoteRepository voteRepository;
    private final FeasibilityReportRepository feasibilityReportRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final UserRepository userRepository;

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
        User investor = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUser.getId()));
        boolean verified = investor.getInvestorStatus() == InvestorStatus.VERIFIED;

        List<Idea> ideas = ideaRepository.findByNeighborhoodIdAndStatusIn(neighborhoodId, OPEN_STATUSES);
        if (ideas.isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> votes = new HashMap<>();
        voteRepository.countByIdeaIds(ideas.stream().map(Idea::getId).toList())
                .forEach(row -> votes.put((Integer) row[0], (Long) row[1]));

        return ideas.stream().map(idea -> {
            long count = votes.getOrDefault(idea.getId(), 0L);
            int threshold = DiscoverService.threshold(idea);
            double[] point = LocationService.pointOf(idea);
            boolean unlocked = opportunityAccessRepository.existsByInvestorIdAndIdeaId(investor.getId(), idea.getId());
            boolean mine = Objects.equals(idea.getOwner().getId(), investor.getId());
            return OpportunityResponse.builder()
                    .id(idea.getId())
                    .title(idea.getTitle())
                    .category(idea.getCategory())
                    .status(idea.getStatus().name())
                    .locationLabel(LocationService.sideOf(idea))
                    .latitude(point != null ? point[0] : null)
                    .longitude(point != null ? point[1] : null)
                    .votesCount(count)
                    .membershipThreshold(threshold)
                    .votePercent(DiscoverService.percent(count, threshold))
                    .gapScore(feasibilityReportRepository.findByIdeaId(idea.getId())
                            .map(FeasibilityReport::getGapScore).orElse(null))
                    .unlocked(unlocked)
                    .canUnlock(verified && !mine && !unlocked && idea.getStatus() == IdeaStatus.COMPLETED)
                    .build();
        }).sorted(Comparator.comparing((OpportunityResponse o) -> isTaken(o.getStatus()))
                .thenComparing(Comparator.comparing(OpportunityResponse::getVotePercent).reversed())).toList();
    }

    // Available opportunities come first, adopted or opened ones after them
    private static boolean isTaken(String status) {
        return IdeaStatus.ADOPTED.name().equals(status) || IdeaStatus.OPENED.name().equals(status);
    }
}
