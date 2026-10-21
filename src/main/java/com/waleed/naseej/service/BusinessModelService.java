package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.BusinessModelRequest;
import com.waleed.naseej.dto.out.BusinessModelResponse;
import com.waleed.naseej.entity.BusinessModel;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.BusinessModelRepository;
import com.waleed.naseej.repository.IdeaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessModelService {

    private final BusinessModelRepository businessModelRepository;
    private final IdeaRepository ideaRepository;

    public List<BusinessModelResponse> getAll() {
        return businessModelRepository.findAll().stream().map(this::toResponse).toList();
    }

    public BusinessModelResponse create(BusinessModelRequest request) {
        BusinessModel entity = BusinessModel.builder()
                .idea(ideaRepository.findById(request.getIdeaId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idea not found with id: " + request.getIdeaId())))
                .valueProposition(request.getValueProposition())
                .targetCustomers(request.getTargetCustomers())
                .revenueStreams(request.getRevenueStreams())
                .costStructure(request.getCostStructure())
                .channels(request.getChannels())
                .estimatedCapitalMin(request.getEstimatedCapitalMin())
                .estimatedCapitalMax(request.getEstimatedCapitalMax())
                .risks(request.getRisks())
                .build();
        return toResponse(businessModelRepository.save(entity));
    }

    public BusinessModelResponse update(Integer id, BusinessModelRequest request) {
        BusinessModel entity = businessModelRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Business model not found with id: " + id));
        entity.setIdea(ideaRepository.findById(request.getIdeaId())
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + request.getIdeaId())));
        entity.setValueProposition(request.getValueProposition());
        entity.setTargetCustomers(request.getTargetCustomers());
        entity.setRevenueStreams(request.getRevenueStreams());
        entity.setCostStructure(request.getCostStructure());
        entity.setChannels(request.getChannels());
        entity.setEstimatedCapitalMin(request.getEstimatedCapitalMin());
        entity.setEstimatedCapitalMax(request.getEstimatedCapitalMax());
        entity.setRisks(request.getRisks());
        return toResponse(businessModelRepository.save(entity));
    }

    public void delete(Integer id) {
        businessModelRepository.delete(businessModelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business model not found with id: " + id)));
    }

    public BusinessModelResponse getById(Integer id) {
        return toResponse(businessModelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business model not found with id: " + id)));
    }

    private BusinessModelResponse toResponse(BusinessModel entity) {
        return BusinessModelResponse.builder()
                .id(entity.getId())
                .ideaId(entity.getIdea().getId())
                .valueProposition(entity.getValueProposition())
                .targetCustomers(entity.getTargetCustomers())
                .revenueStreams(entity.getRevenueStreams())
                .costStructure(entity.getCostStructure())
                .channels(entity.getChannels())
                .estimatedCapitalMin(entity.getEstimatedCapitalMin())
                .estimatedCapitalMax(entity.getEstimatedCapitalMax())
                .risks(entity.getRisks())
                .build();
    }
}
