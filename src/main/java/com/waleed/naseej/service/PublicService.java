package com.waleed.naseej.service;

import com.waleed.naseej.dto.out.NeighborhoodResponse;
import com.waleed.naseej.dto.out.PublicIdeaPinResponse;
import com.waleed.naseej.dto.out.PublicStatsResponse;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.VoteRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What the landing page and the sign-up form can read without an account. */
@Service
@RequiredArgsConstructor
public class PublicService {

    private static final double MAX_RADIUS_KM = 15.0;
    private static final int MAX_PINS = 60;

    private final IdeaRepository ideaRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final VoteRepository voteRepository;
    private final LocationService locationService;

    public PublicStatsResponse stats() {
        List<Neighborhood> neighborhoods = neighborhoodRepository.findAll();
        return PublicStatsResponse.builder()
                .openIdeas(ideaRepository.countByStatus(IdeaStatus.PUBLISHED))
                .completedIdeas(ideaRepository.countByStatus(IdeaStatus.COMPLETED))
                .adoptedIdeas(ideaRepository.countByStatus(IdeaStatus.ADOPTED) + ideaRepository.countByStatus(IdeaStatus.OPENED))
                .totalVotes(voteRepository.count())
                .neighborhoods((long) neighborhoods.size())
                .cities(neighborhoods.stream().map(n -> n.getCity().trim().toLowerCase()).distinct().count())
                .build();
    }

    /** Open ideas near a point: titles and places only. */
    public List<PublicIdeaPinResponse> nearby(double latitude, double longitude, Double radiusKm) {
        double radius = radiusKm == null ? 5.0 : Math.max(0.5, Math.min(MAX_RADIUS_KM, radiusKm));
        return ideaRepository.findByStatusIn(List.of(IdeaStatus.PUBLISHED, IdeaStatus.COMPLETED)).stream()
                .filter(idea -> LocationService.pointOf(idea) != null)
                .map(idea -> new Object[]{idea, distance(latitude, longitude, idea)})
                .filter(pair -> (double) pair[1] <= radius)
                .sorted(Comparator.comparingDouble(pair -> (double) pair[1]))
                .limit(MAX_PINS)
                .map(pair -> {
                    Idea idea = (Idea) pair[0];
                    double[] point = LocationService.pointOf(idea);
                    return PublicIdeaPinResponse.builder()
                            .id(idea.getId())
                            .title(idea.getTitle())
                            .category(idea.getCategory())
                            .neighborhoodName(idea.getNeighborhood().getName())
                            .latitude(point[0])
                            .longitude(point[1])
                            .distanceKm(LocationService.round2((double) pair[1]))
                            .build();
                })
                .toList();
    }

    /** Sign-up: fill the neighborhood from the browser location. */
    public NeighborhoodResponse nearestNeighborhood(double latitude, double longitude) {
        Neighborhood n = locationService.findNearestNeighborhood(latitude, longitude)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Naseej isn't available in your area yet. Choose your neighborhood from the list"));
        return NeighborhoodResponse.builder()
                .id(n.getId())
                .name(n.getName())
                .city(n.getCity())
                .latitude(n.getLatitude())
                .longitude(n.getLongitude())
                .build();
    }

    private static double distance(double lat, double lng, Idea idea) {
        double[] p = LocationService.pointOf(idea);
        return LocationService.distanceKm(lat, lng, p[0], p[1]);
    }
}
