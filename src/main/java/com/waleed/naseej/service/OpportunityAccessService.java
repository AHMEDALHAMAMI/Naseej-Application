package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.OpportunityAccessRequest;
import com.waleed.naseej.dto.out.OpportunityAccessResponse;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.OpportunityAccess;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.AccessType;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ForbiddenException;
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

    /**
     * grant() from the class diagram: an investor opens a completed request.
     * PAID needs a payment of this investor that Moyasar confirmed (status PAID) and that wasn't used before.
     * PARTNERSHIP has no payment; its partnership agreement is linked to the new access afterwards.
     */
    public OpportunityAccessResponse unlock(OpportunityAccessRequest request) {
        User investor = userRepository.findById(request.getInvestorId())
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found with id: " + request.getInvestorId()));
        if (investor.getRole() != Role.INVESTOR) {
            throw new ForbiddenException("Only investors can unlock opportunities");
        }

        Idea idea = ideaRepository.findById(request.getIdeaId())
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found with id: " + request.getIdeaId()));
        if (idea.getStatus() != IdeaStatus.COMPLETED) {
            throw new BadRequestException(
                    "Only completed requests can be unlocked. This idea is " + idea.getStatus());
        }

        if (opportunityAccessRepository.existsByInvestorIdAndIdeaId(investor.getId(), idea.getId())) {
            throw new DuplicateResourceException("This investor already unlocked this opportunity");
        }

        Payment payment = null;
        if (request.getAccessType() == AccessType.PAID) {
            payment = paymentRepository.findById(request.getPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + request.getPaymentId()));
            if (!payment.getUser().getId().equals(investor.getId())) {
                throw new BadRequestException("This payment belongs to another user");
            }
            if (payment.getStatus() != PaymentStatus.PAID) {
                throw new BadRequestException("The payment isn't confirmed yet (status: " + payment.getStatus() + ")");
            }
            if (opportunityAccessRepository.existsByPaymentId(payment.getId())) {
                throw new DuplicateResourceException("This payment was already used to unlock an opportunity");
            }
        }

        OpportunityAccess access = OpportunityAccess.builder()
                .investor(investor)
                .idea(idea)
                .payment(payment)
                .accessType(request.getAccessType())
                .build();
        return toResponse(opportunityAccessRepository.save(access));
    }

    /** Has this investor unlocked this idea? */
    public boolean check(Integer investorId, Integer ideaId) {
        return opportunityAccessRepository.existsByInvestorIdAndIdeaId(investorId, ideaId);
    }

    /** Every opportunity the investor unlocked. */
    public List<OpportunityAccessResponse> getByInvestor(Integer investorId) {
        if (!userRepository.existsById(investorId)) {
            throw new ResourceNotFoundException("Investor not found with id: " + investorId);
        }
        return opportunityAccessRepository.findByInvestorId(investorId).stream().map(this::toResponse).toList();
    }

    /** How many investors unlocked this idea. */
    public long countByIdea(Integer ideaId) {
        if (!ideaRepository.existsById(ideaId)) {
            throw new ResourceNotFoundException("Idea not found with id: " + ideaId);
        }
        return opportunityAccessRepository.countByIdeaId(ideaId);
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
