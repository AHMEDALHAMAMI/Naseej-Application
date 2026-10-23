package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.AcceptPartnershipTermsRequest;
import com.waleed.naseej.dto.in.PartnershipAgreementRequest;
import com.waleed.naseej.dto.out.PartnershipAgreementCheckResponse;
import com.waleed.naseej.dto.out.PartnershipAgreementResponse;
import com.waleed.naseej.entity.PartnershipAgreement;
import com.waleed.naseej.enums.PartnershipAgreementStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PartnershipAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnershipAgreementService {

    private final PartnershipAgreementRepository partnershipAgreementRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;

    public List<PartnershipAgreementResponse> getAll() {
        return partnershipAgreementRepository.findAll().stream().map(this::toResponse).toList();
    }

    public PartnershipAgreementResponse create(PartnershipAgreementRequest request) {
        if (request.getAccessId() == null) {
            throw new BadRequestException("Access id is required");
        }
        if (request.getProfitSharePercent() == null || request.getDurationYears() == null) {
            throw new BadRequestException("Profit share percent and duration years are required");
        }
        if (partnershipAgreementRepository.existsByAccessId(request.getAccessId())) {
            throw new DuplicateResourceException(
                    "Partnership agreement already exists for access id: " + request.getAccessId());
        }

        String status = request.getStatus();
        if (!StringUtils.hasText(status)) {
            status = PartnershipAgreementStatus.DRAFT.name();
        }

        PartnershipAgreement entity = PartnershipAgreement.builder()
                .access(opportunityAccessRepository.findById(request.getAccessId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Opportunity access not found with id: " + request.getAccessId())))
                .profitSharePercent(request.getProfitSharePercent())
                .durationYears(request.getDurationYears())
                .status(status)
                .signedName(request.getSignedName())
                .signatureIp(request.getSignatureIp())
                .signedAt(request.getSignedAt())
                .validUntil(request.getValidUntil())
                .shareStartsAt(request.getShareStartsAt())
                .shareEndsAt(request.getShareEndsAt())
                .build();
        return toResponse(partnershipAgreementRepository.save(entity));
    }

    public PartnershipAgreementResponse acceptTerms(Integer id, AcceptPartnershipTermsRequest request) {
        PartnershipAgreement entity = partnershipAgreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partnership agreement not found with id: " + id));

        if (!Boolean.TRUE.equals(request.getTermsAccepted())) {
            throw new BadRequestException("Partnership terms must be accepted");
        }
        if (!StringUtils.hasText(request.getSignedName())) {
            throw new BadRequestException("Signed name is required when accepting partnership terms");
        }
        if (PartnershipAgreementStatus.SIGNED.name().equals(entity.getStatus()) && entity.getSignedAt() != null) {
            throw new BadRequestException("Partnership agreement terms are already accepted");
        }

        entity.acceptTerms(request.getSignedName().trim(), request.getSignatureIp());
        return toResponse(partnershipAgreementRepository.save(entity));
    }

    public PartnershipAgreementCheckResponse checkTermsByAccessId(Integer accessId) {
        return partnershipAgreementRepository.findByAccessId(accessId)
                .map(agreement -> PartnershipAgreementCheckResponse.builder()
                        .accessId(accessId)
                        .agreementId(agreement.getId())
                        .agreementExists(true)
                        .termsAccepted(isTermsAccepted(agreement))
                        .build())
                .orElse(PartnershipAgreementCheckResponse.builder()
                        .accessId(accessId)
                        .agreementExists(false)
                        .termsAccepted(false)
                        .build());
    }

    public boolean isTermsAcceptedForAccess(Integer accessId) {
        return partnershipAgreementRepository.findByAccessId(accessId)
                .map(this::isTermsAccepted)
                .orElse(false);
    }

    public PartnershipAgreementResponse update(Integer id, PartnershipAgreementRequest request) {
        PartnershipAgreement entity = partnershipAgreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partnership agreement not found with id: " + id));
        entity.setAccess(opportunityAccessRepository.findById(request.getAccessId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Opportunity access not found with id: " + request.getAccessId())));
        entity.setProfitSharePercent(request.getProfitSharePercent());
        entity.setDurationYears(request.getDurationYears());
        entity.setStatus(request.getStatus());
        entity.setSignedName(request.getSignedName());
        entity.setSignatureIp(request.getSignatureIp());
        entity.setSignedAt(request.getSignedAt());
        entity.setValidUntil(request.getValidUntil());
        entity.setShareStartsAt(request.getShareStartsAt());
        entity.setShareEndsAt(request.getShareEndsAt());
        return toResponse(partnershipAgreementRepository.save(entity));
    }

    public void delete(Integer id) {
        partnershipAgreementRepository.delete(partnershipAgreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partnership agreement not found with id: " + id)));
    }

    public PartnershipAgreementResponse getById(Integer id) {
        return toResponse(partnershipAgreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partnership agreement not found with id: " + id)));
    }

    private boolean isTermsAccepted(PartnershipAgreement agreement) {
        return PartnershipAgreementStatus.SIGNED.name().equals(agreement.getStatus())
                && agreement.getSignedAt() != null
                && StringUtils.hasText(agreement.getSignedName());
    }

    private PartnershipAgreementResponse toResponse(PartnershipAgreement entity) {
        return PartnershipAgreementResponse.builder()
                .id(entity.getId())
                .accessId(entity.getAccess().getId())
                .profitSharePercent(entity.getProfitSharePercent())
                .durationYears(entity.getDurationYears())
                .status(entity.getStatus())
                .signedName(entity.getSignedName())
                .signatureIp(entity.getSignatureIp())
                .signedAt(entity.getSignedAt())
                .validUntil(entity.getValidUntil())
                .shareStartsAt(entity.getShareStartsAt())
                .shareEndsAt(entity.getShareEndsAt())
                .build();
    }
}
