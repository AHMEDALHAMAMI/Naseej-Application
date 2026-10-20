package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.IdeaCheckRequest;
import com.waleed.naseej.dto.out.IdeaCheckResponse;
import com.waleed.naseej.entity.IdeaCheck;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.IdeaCheckRepository;
import com.waleed.naseej.repository.IdeaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IdeaCheckService {

    private final IdeaCheckRepository ideaCheckRepository;
    private final IdeaRepository ideaRepository;

    public List<IdeaCheckResponse> getAll() {
        return ideaCheckRepository.findAll().stream().map(this::toResponse).toList();
    }

    public IdeaCheckResponse create(IdeaCheckRequest request) {
        IdeaCheck entity = IdeaCheck.builder()
                .idea(ideaRepository.findById(request.getIdeaId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idea not found with id: " + request.getIdeaId())))
                .similarIdea(request.getSimilarIdeaId() != null
                        ? ideaRepository.findById(request.getSimilarIdeaId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Similar idea not found with id: " + request.getSimilarIdeaId()))
                        : null)
                .score(request.getScore())
                .decision(request.getDecision())
                .searchRadiusKm(request.getSearchRadiusKm())
                .competitorsCount(request.getCompetitorsCount())
                .nearestCompetitorKm(request.getNearestCompetitorKm())
                .scarcityFound(request.getScarcityFound())
                .feasibilitySummary(request.getFeasibilitySummary())
                .build();
        return toResponse(ideaCheckRepository.save(entity));
    }

    public IdeaCheckResponse update(Integer id, IdeaCheckRequest request) {
        IdeaCheck entity = ideaCheckRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Idea check not found with id: " + id));
        entity.setIdea(ideaRepository.findById(request.getIdeaId())
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + request.getIdeaId())));
        entity.setSimilarIdea(request.getSimilarIdeaId() != null
                ? ideaRepository.findById(request.getSimilarIdeaId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Similar idea not found with id: " + request.getSimilarIdeaId()))
                : null);
        entity.setScore(request.getScore());
        entity.setDecision(request.getDecision());
        entity.setSearchRadiusKm(request.getSearchRadiusKm());
        entity.setCompetitorsCount(request.getCompetitorsCount());
        entity.setNearestCompetitorKm(request.getNearestCompetitorKm());
        entity.setScarcityFound(request.getScarcityFound());
        entity.setFeasibilitySummary(request.getFeasibilitySummary());
        return toResponse(ideaCheckRepository.save(entity));
    }

    public void delete(Integer id) {
        ideaCheckRepository.delete(ideaCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Idea check not found with id: " + id)));
    }

    public IdeaCheckResponse getById(Integer id) {
        return toResponse(ideaCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Idea check not found with id: " + id)));
    }

    private IdeaCheckResponse toResponse(IdeaCheck entity) {
        return IdeaCheckResponse.builder()
                .id(entity.getId())
                .ideaId(entity.getIdea().getId())
                .similarIdeaId(entity.getSimilarIdea() != null ? entity.getSimilarIdea().getId() : null)
                .score(entity.getScore())
                .decision(entity.getDecision())
                .searchRadiusKm(entity.getSearchRadiusKm())
                .competitorsCount(entity.getCompetitorsCount())
                .nearestCompetitorKm(entity.getNearestCompetitorKm())
                .scarcityFound(entity.getScarcityFound())
                .feasibilitySummary(entity.getFeasibilitySummary())
                .build();
    }
}
