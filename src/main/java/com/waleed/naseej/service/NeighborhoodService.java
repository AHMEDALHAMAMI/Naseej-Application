package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.NeighborhoodRequest;
import com.waleed.naseej.dto.out.NeighborhoodResponse;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.NeighborhoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NeighborhoodService {

    private final NeighborhoodRepository neighborhoodRepository;

    public List<NeighborhoodResponse> getAll() {
        return neighborhoodRepository.findAll().stream().map(this::toResponse).toList();
    }

    public NeighborhoodResponse create(NeighborhoodRequest request) {
        Neighborhood entity = Neighborhood.builder()
                .name(request.getName())
                .city(request.getCity())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
        return toResponse(neighborhoodRepository.save(entity));
    }

    public NeighborhoodResponse update(Integer id, NeighborhoodRequest request) {
        Neighborhood entity = neighborhoodRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Neighborhood not found with id: " + id));
        entity.setName(request.getName());
        entity.setCity(request.getCity());
        entity.setLatitude(request.getLatitude());
        entity.setLongitude(request.getLongitude());
        return toResponse(neighborhoodRepository.save(entity));
    }

    public void delete(Integer id) {
        Neighborhood entity = neighborhoodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Neighborhood not found with id: " + id));

        neighborhoodRepository.delete(entity);
    }

    public NeighborhoodResponse getById(Integer id) {
        return toResponse(neighborhoodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Neighborhood not found with id: " + id)));
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
