package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.IdeaRequest;
import com.waleed.naseej.dto.out.IdeaResponse;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final NeighborhoodRepository neighborhoodRepository;

    public List<IdeaResponse> getAll() {
        return ideaRepository.findAll().stream().map(this::toResponse).toList();
    }

    public IdeaResponse create(IdeaRequest request) {
        Idea entity = Idea.builder()
                .owner(userRepository.findById(request.getOwnerId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found with id: " + request.getOwnerId())))
                .neighborhood(neighborhoodRepository.findById(request.getNeighborhoodId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Neighborhood not found with id: " + request.getNeighborhoodId())))
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .status(request.getStatus() != null ? request.getStatus() : IdeaStatus.SCREENING)
                .membershipThreshold(
                        request.getMembershipThreshold() != null ? request.getMembershipThreshold() : 150)
                .publishedAt(request.getPublishedAt())
                .confirmedAt(request.getConfirmedAt())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
        return toResponse(ideaRepository.save(entity));
    }

    public IdeaResponse update(Integer id, IdeaRequest request) {
        Idea entity = ideaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + id));
        entity.setOwner(userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getOwnerId())));
        entity.setNeighborhood(neighborhoodRepository.findById(request.getNeighborhoodId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Neighborhood not found with id: " + request.getNeighborhoodId())));
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setCategory(request.getCategory());
        entity.setStatus(request.getStatus());
        entity.setMembershipThreshold(request.getMembershipThreshold());
        entity.setPublishedAt(request.getPublishedAt());
        entity.setConfirmedAt(request.getConfirmedAt());
        // Keep the current spot unless a new one is sent
        if (request.getLatitude() != null && request.getLongitude() != null) {
            entity.setLatitude(request.getLatitude());
            entity.setLongitude(request.getLongitude());
        }
        return toResponse(ideaRepository.save(entity));
    }

    public void delete(Integer id) {
        ideaRepository.delete(ideaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + id)));
    }

    public IdeaResponse getById(Integer id) {
        return toResponse(ideaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + id)));
    }

    private IdeaResponse toResponse(Idea entity) {
        return IdeaResponse.builder()
                .id(entity.getId())
                .ownerId(entity.getOwner().getId())
                .neighborhoodId(entity.getNeighborhood().getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .status(entity.getStatus())
                .membershipThreshold(entity.getMembershipThreshold())
                .publishedAt(entity.getPublishedAt())
                .confirmedAt(entity.getConfirmedAt())
                .build();
    }
}
