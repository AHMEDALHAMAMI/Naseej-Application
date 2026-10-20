package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ContractSignatureRequest;
import com.waleed.naseej.dto.out.ContractSignatureResponse;
import com.waleed.naseej.entity.ContractSignature;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.ContractSignatureRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractSignatureService {

    private final ContractSignatureRepository contractSignatureRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;

    public List<ContractSignatureResponse> getAll() {
        return contractSignatureRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ContractSignatureResponse create(ContractSignatureRequest request) {
        ContractSignature entity = ContractSignature.builder()
                .contract(contractRepository.findById(request.getContractId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Contract not found with id: " + request.getContractId())))
                .signer(userRepository.findById(request.getSignerId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Signer not found with id: " + request.getSignerId())))
                .signerRole(request.getSignerRole())
                .typedName(request.getTypedName())
                .signatureImageUri(request.getSignatureImageUri())
                .ipAddress(request.getIpAddress())
                .otpVerified(Boolean.TRUE.equals(request.getOtpVerified()))
                .termsAccepted(Boolean.TRUE.equals(request.getTermsAccepted()))
                .signedAt(request.getSignedAt())
                .build();
        return toResponse(contractSignatureRepository.save(entity));
    }

    public ContractSignatureResponse update(Integer id, ContractSignatureRequest request) {
        ContractSignature entity = contractSignatureRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Contract signature not found with id: " + id));
        entity.setContract(contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contract not found with id: " + request.getContractId())));
        entity.setSigner(userRepository.findById(request.getSignerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Signer not found with id: " + request.getSignerId())));
        entity.setSignerRole(request.getSignerRole());
        entity.setTypedName(request.getTypedName());
        entity.setSignatureImageUri(request.getSignatureImageUri());
        entity.setIpAddress(request.getIpAddress());
        entity.setOtpVerified(Boolean.TRUE.equals(request.getOtpVerified()));
        entity.setTermsAccepted(Boolean.TRUE.equals(request.getTermsAccepted()));
        entity.setSignedAt(request.getSignedAt());
        return toResponse(contractSignatureRepository.save(entity));
    }

    public void delete(Integer id) {
        contractSignatureRepository.delete(contractSignatureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract signature not found with id: " + id)));
    }

    public ContractSignatureResponse getById(Integer id) {
        return toResponse(contractSignatureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract signature not found with id: " + id)));
    }

    private ContractSignatureResponse toResponse(ContractSignature entity) {
        return ContractSignatureResponse.builder()
                .id(entity.getId())
                .contractId(entity.getContract().getId())
                .signerId(entity.getSigner().getId())
                .signerRole(entity.getSignerRole())
                .typedName(entity.getTypedName())
                .signatureImageUri(entity.getSignatureImageUri())
                .ipAddress(entity.getIpAddress())
                .otpVerified(entity.isOtpVerified())
                .termsAccepted(entity.isTermsAccepted())
                .signedAt(entity.getSignedAt())
                .build();
    }
}
