package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.NearbyPlaceRequest;
import com.waleed.naseej.dto.out.NearbyPlaceResponse;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.IdeaCheck;
import com.waleed.naseej.entity.NearbyPlace;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.IdeaCheckRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NearbyPlaceRepository;
import com.waleed.naseej.service.GooglePlacesService.FoundPlace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NearbyPlaceService {

    private final NearbyPlaceRepository nearbyPlaceRepository;
    private final IdeaRepository ideaRepository;
    private final IdeaCheckRepository ideaCheckRepository;
    private final GooglePlacesService googlePlacesService;
    private final TransactionTemplate transactionTemplate;

    public List<NearbyPlaceResponse> getAll() {
        return nearbyPlaceRepository.findAll().stream().map(this::toResponse).toList();
    }

    public NearbyPlaceResponse create(NearbyPlaceRequest request) {
        NearbyPlace entity = NearbyPlace.builder()
                .idea(ideaRepository.findById(request.getIdeaId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idea not found with id: " + request.getIdeaId())))
                .googlePlaceId(request.getGooglePlaceId())
                .name(request.getName())
                .category(request.getCategory())
                .distanceKm(request.getDistanceKm())
                .build();
        return toResponse(nearbyPlaceRepository.save(entity));
    }

    public NearbyPlaceResponse update(Integer id, NearbyPlaceRequest request) {
        NearbyPlace entity = nearbyPlaceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Nearby place not found with id: " + id));
        entity.setIdea(ideaRepository.findById(request.getIdeaId())
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + request.getIdeaId())));
        entity.setGooglePlaceId(request.getGooglePlaceId());
        entity.setName(request.getName());
        entity.setCategory(request.getCategory());
        entity.setDistanceKm(request.getDistanceKm());
        return toResponse(nearbyPlaceRepository.save(entity));
    }

    public void delete(Integer id) {
        nearbyPlaceRepository.delete(nearbyPlaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nearby place not found with id: " + id)));
    }

    public NearbyPlaceResponse getById(Integer id) {
        return toResponse(nearbyPlaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nearby place not found with id: " + id)));
    }

    /** The idea's competitors, nearest first. */
    public List<NearbyPlaceResponse> getByIdea(Integer ideaId) {
        requireIdea(ideaId);
        return nearbyPlaceRepository.findByIdeaIdOrderByDistanceKmAsc(ideaId).stream().map(this::toResponse).toList();
    }

    /** How many competitors are within {@code radiusKm} of the idea's neighborhood. */
    public long countByIdea(Integer ideaId, Double radiusKm) {
        if (radiusKm == null || radiusKm <= 0) {
            throw new BadRequestException("Radius must be more than 0 km");
        }
        requireIdea(ideaId);
        return nearbyPlaceRepository.countByIdeaIdAndDistanceKmLessThanEqual(ideaId, radiusKm);
    }

    /**
     * Searches Google Places again with the type and radius from the idea check, and replaces the
     * saved competitors. It doesn't change the check's score or decision; run the check again for that.
     */
    public List<NearbyPlaceResponse> refresh(Integer ideaId) {
        Idea idea = ideaRepository.findWithNeighborhoodById(ideaId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + ideaId));
        IdeaCheck check = ideaCheckRepository.findByIdeaId(ideaId)
                .orElseThrow(() -> new BadRequestException(
                        "Run the idea check first, it decides which places count as competitors"));
        if (check.getPlaceType() == null) {
            throw new BadRequestException(
                    "This idea check was entered manually and has no place type. Run the idea check first");
        }

        Neighborhood neighborhood = idea.getNeighborhood();
        if (neighborhood.getLatitude() == null || neighborhood.getLongitude() == null) {
            throw new BadRequestException("The idea's neighborhood has no latitude/longitude, so competitors can't be searched");
        }

        List<FoundPlace> found = googlePlacesService.searchNearby(
                neighborhood.getLatitude(), neighborhood.getLongitude(), check.getSearchRadiusKm(), check.getPlaceType());
        replaceForIdea(idea, found);
        return getByIdea(ideaId);
    }

    /**
     * Deletes the idea's saved competitors and saves {@code places} instead.
     * Runs in one transaction, and joins the caller's transaction when there is one.
     */
    public List<NearbyPlace> replaceForIdea(Idea idea, List<FoundPlace> places) {
        // Google shouldn't repeat a place, but (idea, place id) is unique in the table, so make sure
        Map<String, FoundPlace> unique = new LinkedHashMap<>();
        places.forEach(place -> unique.putIfAbsent(place.googlePlaceId(), place));

        List<NearbyPlace> rows = unique.values().stream()
                .map(place -> NearbyPlace.builder()
                        .idea(idea)
                        .googlePlaceId(place.googlePlaceId())
                        .name(truncate(place.name(), 255))
                        .category(truncate(place.primaryType(), 50))
                        .distanceKm(place.distanceKm())
                        .build())
                .toList();

        return transactionTemplate.execute(status -> {
            nearbyPlaceRepository.deleteAllForIdea(idea.getId());
            return nearbyPlaceRepository.saveAll(rows);
        });
    }

    private void requireIdea(Integer ideaId) {
        if (!ideaRepository.existsById(ideaId)) {
            throw new ResourceNotFoundException("Idea not found with id: " + ideaId);
        }
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private NearbyPlaceResponse toResponse(NearbyPlace entity) {
        return NearbyPlaceResponse.builder()
                .id(entity.getId())
                .ideaId(entity.getIdea().getId())
                .googlePlaceId(entity.getGooglePlaceId())
                .name(entity.getName())
                .category(entity.getCategory())
                .distanceKm(entity.getDistanceKm())
                .build();
    }
}
