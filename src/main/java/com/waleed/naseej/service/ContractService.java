package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ContractRequest;
import com.waleed.naseej.dto.out.ContractResponse;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;

    public List<ContractResponse> getAll() {
        return contractRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ContractResponse create(ContractRequest request) {
        if (contractRepository.existsByContractNumber(request.getContractNumber())) {
            throw new DuplicateResourceException(
                    "Contract number already exists: " + request.getContractNumber());
        }
        if (contractRepository.existsByAccessId(request.getAccessId())) {
            throw new DuplicateResourceException(
                    "A contract already exists for access id: " + request.getAccessId());
        }

        Contract contract = Contract.builder()
                .contractNumber(request.getContractNumber())
                .idea(ideaRepository.findById(request.getIdeaId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idea not found with id: " + request.getIdeaId())))
                .investor(userRepository.findById(request.getInvestorId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Investor not found with id: " + request.getInvestorId())))
                .ideaOwner(userRepository.findById(request.getIdeaOwnerId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idea owner not found with id: " + request.getIdeaOwnerId())))
                .access(opportunityAccessRepository.findById(request.getAccessId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Opportunity access not found with id: " + request.getAccessId())))
                .foundingMemberDiscount(request.getFoundingMemberDiscount())
                .ideaOwnerDiscount(request.getIdeaOwnerDiscount())
                .openingDeadline(request.getOpeningDeadline())
                .status(ContractStatus.DRAFT)
                .build();

        return toResponse(contractRepository.save(contract));
    }

    public ContractResponse update(Integer id, ContractRequest request) {
        Contract contract = contractRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        contract.setContractNumber(request.getContractNumber());
        contract.setIdea(ideaRepository.findById(request.getIdeaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Idea not found with id: " + request.getIdeaId())));
        contract.setInvestor(userRepository.findById(request.getInvestorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investor not found with id: " + request.getInvestorId())));
        contract.setIdeaOwner(userRepository.findById(request.getIdeaOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Idea owner not found with id: " + request.getIdeaOwnerId())));
        contract.setAccess(opportunityAccessRepository.findById(request.getAccessId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Opportunity access not found with id: " + request.getAccessId())));
        contract.setFoundingMemberDiscount(request.getFoundingMemberDiscount());
        contract.setIdeaOwnerDiscount(request.getIdeaOwnerDiscount());
        contract.setStatus(request.getStatus());
        contract.setOpeningDeadline(request.getOpeningDeadline());
        contract.setPdfUrl(request.getPdfUrl());
        contract.setEmailSentAt(request.getEmailSentAt());
        contract.setActivatedAt(request.getActivatedAt());
        contract.setOpenedAt(request.getOpenedAt());
        return toResponse(contractRepository.save(contract));
    }

    public void delete(Integer id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT contracts can be deleted");
        }
        contractRepository.delete(contract);
    }

    public ContractResponse getById(Integer id) {
        return toResponse(contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id)));
    }

    private ContractResponse toResponse(Contract contract) {
        return ContractResponse.builder()
                .id(contract.getId())
                .contractNumber(contract.getContractNumber())
                .ideaId(contract.getIdea().getId())
                .investorId(contract.getInvestor().getId())
                .ideaOwnerId(contract.getIdeaOwner().getId())
                .accessId(contract.getAccess().getId())
                .foundingMemberDiscount(contract.getFoundingMemberDiscount())
                .ideaOwnerDiscount(contract.getIdeaOwnerDiscount())
                .status(contract.getStatus())
                .openingDeadline(contract.getOpeningDeadline())
                .pdfUrl(contract.getPdfUrl())
                .emailSentAt(contract.getEmailSentAt())
                .activatedAt(contract.getActivatedAt())
                .openedAt(contract.getOpenedAt())
                .build();
    }
}
