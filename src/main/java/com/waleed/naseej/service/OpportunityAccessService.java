package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.OpportunityAccessRequest;
import com.waleed.naseej.dto.out.OpportunityAccessResponse;
import com.waleed.naseej.entity.OpportunityAccess;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpportunityAccessService {

    private final OpportunityAccessRepository opportunityAccessRepository;
    private final UserRepository userRepository;
    private final IdeaRepository ideaRepository;
    private final PaymentRepository paymentRepository;

    public List<OpportunityAccessResponse> getAll() {
        return opportunityAccessRepository.findAll().stream().map(this::toResponse).toList();
    }

    public OpportunityAccessResponse create(OpportunityAccessRequest request) {
        OpportunityAccess entity = OpportunityAccess.builder()
                .investor(userRepository.findById(request.getInvestorId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Investor not found with id: " + request.getInvestorId())))
                .idea(ideaRepository.findById(request.getIdeaId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idea not found with id: " + request.getIdeaId())))
                .payment(request.getPaymentId() != null
                        ? paymentRepository.findById(request.getPaymentId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Payment not found with id: " + request.getPaymentId()))
                        : null)
                .accessType(request.getAccessType())
                .priorityUntil(request.getPriorityUntil())
                .build();
        return toResponse(opportunityAccessRepository.save(entity));
    }

    public OpportunityAccessResponse update(Integer id, OpportunityAccessRequest request) {
        OpportunityAccess entity = opportunityAccessRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Opportunity access not found with id: " + id));
        entity.setInvestor(userRepository.findById(request.getInvestorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investor not found with id: " + request.getInvestorId())));
        entity.setIdea(ideaRepository.findById(request.getIdeaId())
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + request.getIdeaId())));
        entity.setPayment(request.getPaymentId() != null
                ? paymentRepository.findById(request.getPaymentId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payment not found with id: " + request.getPaymentId()))
                : null);
        entity.setAccessType(request.getAccessType());
        entity.setPriorityUntil(request.getPriorityUntil());
        return toResponse(opportunityAccessRepository.save(entity));
    }

    public void delete(Integer id) {
        opportunityAccessRepository.delete(opportunityAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity access not found with id: " + id)));
    }

    public OpportunityAccessResponse getById(Integer id) {
        return toResponse(opportunityAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity access not found with id: " + id)));
    }

    private OpportunityAccessResponse toResponse(OpportunityAccess entity) {
        return OpportunityAccessResponse.builder()
                .id(entity.getId())
                .investorId(entity.getInvestor().getId())
                .ideaId(entity.getIdea().getId())
                .paymentId(entity.getPayment() != null ? entity.getPayment().getId() : null)
                .accessType(entity.getAccessType())
                .priorityUntil(entity.getPriorityUntil())
                .build();
    }
}
