package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ContractSignatureRequest;
import com.waleed.naseej.dto.out.ContractSignatureResponse;
import com.waleed.naseej.dto.out.ContractSigningCheckResponse;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.ContractSignature;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.ContractSignatureRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContractSignatureService {

    private static final String ROLE_INVESTOR = "INVESTOR";
    private static final String ROLE_IDEA_OWNER = "IDEA_OWNER";

    private final ContractSignatureRepository contractSignatureRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;

    public List<ContractSignatureResponse> getAll() {
        return contractSignatureRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ContractSignatureResponse create(ContractSignatureRequest request) {
        if (request.getContractId() == null) {
            throw new BadRequestException("Contract id is required");
        }
        return signForContract(request.getContractId(), request);
    }

    @Transactional
    public ContractSignatureResponse signForContract(Integer contractId, ContractSignatureRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + contractId));

        if (contract.getStatus() == ContractStatus.ACTIVE
                || contract.getStatus() == ContractStatus.TERMINATED) {
            throw new BadRequestException("Cannot sign a contract in status: " + contract.getStatus());
        }

        if (!Boolean.TRUE.equals(request.getOtpVerified()) || !Boolean.TRUE.equals(request.getTermsAccepted())) {
            throw new BadRequestException("OTP verification and terms acceptance are required to sign");
        }

        String signerRole = normalizeSignerRole(request.getSignerRole());
        validateSignerForRole(contract, request.getSignerId(), signerRole);

        if (contractSignatureRepository.existsByContractIdAndSignerRole(contractId, signerRole)) {
            throw new DuplicateResourceException("This party has already signed the contract");
        }

        ContractSignature signature = ContractSignature.builder()
                .contract(contract)
                .signer(userRepository.findById(request.getSignerId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Signer not found with id: " + request.getSignerId())))
                .signerRole(signerRole)
                .typedName(request.getTypedName())
                .signatureImageUri(request.getSignatureImageUri())
                .ipAddress(request.getIpAddress())
                .otpVerified(true)
                .termsAccepted(true)
                .signedAt(request.getSignedAt() != null ? request.getSignedAt() : LocalDateTime.now())
                .build();
        contract.sign(signature);
        contractSignatureRepository.save(signature);
        contractRepository.save(contract);
        return toResponse(signature);
    }

    public ContractSigningCheckResponse checkSigningStatus(Integer contractId) {
        contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + contractId));

        boolean investorSigned = contractSignatureRepository.existsByContractIdAndSignerRole(
                contractId, ROLE_INVESTOR);
        boolean ideaOwnerSigned = contractSignatureRepository.existsByContractIdAndSignerRole(
                contractId, ROLE_IDEA_OWNER);
        boolean investorTermsAccepted = contractSignatureRepository
                .existsByContractIdAndSignerRoleAndOtpVerifiedTrueAndTermsAcceptedTrue(
                        contractId, ROLE_INVESTOR);
        boolean ideaOwnerTermsAccepted = contractSignatureRepository
                .existsByContractIdAndSignerRoleAndOtpVerifiedTrueAndTermsAcceptedTrue(
                        contractId, ROLE_IDEA_OWNER);

        return ContractSigningCheckResponse.builder()
                .contractId(contractId)
                .investorSigned(investorSigned)
                .ideaOwnerSigned(ideaOwnerSigned)
                .bothPartiesSigned(investorSigned && ideaOwnerSigned)
                .investorTermsAccepted(investorTermsAccepted)
                .ideaOwnerTermsAccepted(ideaOwnerTermsAccepted)
                .bothPartiesTermsAccepted(investorTermsAccepted && ideaOwnerTermsAccepted)
                .build();
    }

    public boolean bothPartiesSigned(Integer contractId) {
        return contractSignatureRepository.existsByContractIdAndSignerRole(contractId, ROLE_INVESTOR)
                && contractSignatureRepository.existsByContractIdAndSignerRole(contractId, ROLE_IDEA_OWNER);
    }

    public boolean bothPartiesTermsAccepted(Integer contractId) {
        return contractSignatureRepository
                .existsByContractIdAndSignerRoleAndOtpVerifiedTrueAndTermsAcceptedTrue(
                        contractId, ROLE_INVESTOR)
                && contractSignatureRepository
                        .existsByContractIdAndSignerRoleAndOtpVerifiedTrueAndTermsAcceptedTrue(
                                contractId, ROLE_IDEA_OWNER);
    }

    public ContractSignatureResponse update(Integer id, ContractSignatureRequest request) {
        ContractSignature entity = contractSignatureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract signature not found with id: " + id));
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

    private void validateSignerForRole(Contract contract, Integer signerId, String signerRole) {
        if (signerId == null) {
            throw new BadRequestException("Signer id is required");
        }
        if (ROLE_INVESTOR.equals(signerRole)
                && !Objects.equals(contract.getInvestor().getId(), signerId)) {
            throw new BadRequestException("Signer id does not match contract investor");
        }
        if (ROLE_IDEA_OWNER.equals(signerRole)
                && !Objects.equals(contract.getIdeaOwner().getId(), signerId)) {
            throw new BadRequestException("Signer id does not match contract idea owner");
        }
        if (!ROLE_INVESTOR.equals(signerRole) && !ROLE_IDEA_OWNER.equals(signerRole)) {
            throw new BadRequestException("Signer role must be INVESTOR or IDEA_OWNER");
        }
    }

    private String normalizeSignerRole(String signerRole) {
        if (signerRole == null || signerRole.isBlank()) {
            throw new BadRequestException("Signer role is required");
        }
        return signerRole.trim().toUpperCase();
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
