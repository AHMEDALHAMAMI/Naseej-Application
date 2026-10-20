package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.NearbyPlaceRequest;
import com.waleed.naseej.dto.out.NearbyPlaceResponse;
import com.waleed.naseej.entity.NearbyPlace;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NearbyPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NearbyPlaceService {

    private final NearbyPlaceRepository nearbyPlaceRepository;
    private final IdeaRepository ideaRepository;

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
