package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.NeighborhoodRequest;
import com.waleed.naseej.dto.out.NeighborhoodResponse;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NeighborhoodService {

    private final NeighborhoodRepository neighborhoodRepository;
    private final UserRepository userRepository;
    private final IdeaRepository ideaRepository;

    public List<NeighborhoodResponse> getAll() {
        return neighborhoodRepository.findAll().stream().map(this::toResponse).toList();
    }

    public NeighborhoodResponse getById(Integer id) {
        return toResponse(findNeighborhood(id));
    }

    /** All neighborhoods of one city, sorted by name (used by the sign-up page). */
    public List<NeighborhoodResponse> getByCity(String city) {
        return neighborhoodRepository.findByCityIgnoreCaseOrderByNameAsc(city.trim())
                .stream().map(this::toResponse).toList();
    }

    /** Search by part of the name, e.g. "narj" finds "Al Narjis". */
    public List<NeighborhoodResponse> search(String keyword) {
        if (keyword.trim().length() < 2) {
            throw new BadRequestException("Search keyword must be at least 2 characters");
        }
        return neighborhoodRepository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword.trim())
                .stream().map(this::toResponse).toList();
    }

    public NeighborhoodResponse create(NeighborhoodRequest request) {
        String name = request.getName().trim();
        String city = request.getCity().trim();

        if (neighborhoodRepository.existsByNameIgnoreCaseAndCityIgnoreCase(name, city)) {
            throw new DuplicateResourceException("Neighborhood '" + name + "' already exists in " + city);
        }

        Neighborhood entity = Neighborhood.builder()
                .name(name)
                .city(city)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
        return toResponse(neighborhoodRepository.save(entity));
    }

    public NeighborhoodResponse update(Integer id, NeighborhoodRequest request) {
        Neighborhood entity = findNeighborhood(id);
        String name = request.getName().trim();
        String city = request.getCity().trim();

        if (neighborhoodRepository.existsByNameIgnoreCaseAndCityIgnoreCaseAndIdNot(name, city, id)) {
            throw new DuplicateResourceException("Neighborhood '" + name + "' already exists in " + city);
        }

        entity.setName(name);
        entity.setCity(city);
        entity.setLatitude(request.getLatitude());
        entity.setLongitude(request.getLongitude());
        return toResponse(neighborhoodRepository.save(entity));
    }

    /** Blocked when users or ideas still belong to this neighborhood (otherwise MySQL fails with a 500). */
    public void delete(Integer id) {
        Neighborhood entity = findNeighborhood(id);

        if (userRepository.existsByNeighborhoodId(id)) {
            throw new BadRequestException("Cannot delete: users are registered in this neighborhood");
        }
        if (ideaRepository.existsByNeighborhoodId(id)) {
            throw new BadRequestException("Cannot delete: ideas are linked to this neighborhood");
        }
        neighborhoodRepository.delete(entity);
    }

    private Neighborhood findNeighborhood(Integer id) {
        return neighborhoodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Neighborhood not found with id: " + id));
    }

    private NeighborhoodResponse toResponse(Neighborhood entity) {
        return NeighborhoodResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .city(entity.getCity())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .build();
    }
}