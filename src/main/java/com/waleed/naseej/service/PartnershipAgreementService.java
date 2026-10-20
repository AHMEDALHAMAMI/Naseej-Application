package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.PartnershipAgreementRequest;
import com.waleed.naseej.dto.out.PartnershipAgreementResponse;
import com.waleed.naseej.entity.PartnershipAgreement;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PartnershipAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
        PartnershipAgreement entity = PartnershipAgreement.builder()
                .access(opportunityAccessRepository.findById(request.getAccessId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Opportunity access not found with id: " + request.getAccessId())))
                .profitSharePercent(request.getProfitSharePercent())
                .durationYears(request.getDurationYears())
                .status(request.getStatus())
                .signedName(request.getSignedName())
                .signatureIp(request.getSignatureIp())
                .signedAt(request.getSignedAt())
                .validUntil(request.getValidUntil())
                .shareStartsAt(request.getShareStartsAt())
                .shareEndsAt(request.getShareEndsAt())
                .build();
        return toResponse(partnershipAgreementRepository.save(entity));
    }

    public PartnershipAgreementResponse update(Integer id, PartnershipAgreementRequest request) {
        PartnershipAgreement entity = partnershipAgreementRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Partnership agreement not found with id: " + id));
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
