package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.FeasibilityReportRequest;
import com.waleed.naseej.dto.out.FeasibilityReportResponse;
import com.waleed.naseej.entity.FeasibilityReport;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.FeasibilityReportRepository;
import com.waleed.naseej.repository.IdeaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeasibilityReportService {

    private final FeasibilityReportRepository feasibilityReportRepository;
    private final IdeaRepository ideaRepository;

    public List<FeasibilityReportResponse> getAll() {
        return feasibilityReportRepository.findAll().stream().map(this::toResponse).toList();
    }

    public FeasibilityReportResponse create(FeasibilityReportRequest request) {
        FeasibilityReport entity = FeasibilityReport.builder()
                .idea(ideaRepository.findById(request.getIdeaId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idea not found with id: " + request.getIdeaId())))
                .gapScore(request.getGapScore())
                .memberGrowthPercent(request.getMemberGrowthPercent())
                .peakDemandTime(request.getPeakDemandTime())
                .recommendation(request.getRecommendation())
                .risks(request.getRisks())
                .build();
        return toResponse(feasibilityReportRepository.save(entity));
    }

    public FeasibilityReportResponse update(Integer id, FeasibilityReportRequest request) {
        FeasibilityReport entity = feasibilityReportRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Feasibility report not found with id: " + id));
        entity.setIdea(ideaRepository.findById(request.getIdeaId())
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + request.getIdeaId())));
        entity.setGapScore(request.getGapScore());
        entity.setMemberGrowthPercent(request.getMemberGrowthPercent());
        entity.setPeakDemandTime(request.getPeakDemandTime());
        entity.setRecommendation(request.getRecommendation());
        entity.setRisks(request.getRisks());
        return toResponse(feasibilityReportRepository.save(entity));
    }

    public void delete(Integer id) {
        feasibilityReportRepository.delete(feasibilityReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feasibility report not found with id: " + id)));
    }

    public FeasibilityReportResponse getById(Integer id) {
        return toResponse(feasibilityReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feasibility report not found with id: " + id)));
    }

    private FeasibilityReportResponse toResponse(FeasibilityReport entity) {
        return FeasibilityReportResponse.builder()
                .id(entity.getId())
                .ideaId(entity.getIdea().getId())
                .gapScore(entity.getGapScore())
                .memberGrowthPercent(entity.getMemberGrowthPercent())
                .peakDemandTime(entity.getPeakDemandTime())
                .recommendation(entity.getRecommendation())
                .risks(entity.getRisks())
                .build();
    }
}
