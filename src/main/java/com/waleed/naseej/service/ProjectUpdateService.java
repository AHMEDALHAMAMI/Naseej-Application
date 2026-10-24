package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ProjectUpdateRequest;
import com.waleed.naseej.dto.out.ProjectUpdateResponse;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.ProjectUpdate;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.ProjectUpdateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectUpdateService {

    private final ProjectUpdateRepository projectUpdateRepository;
    private final ContractRepository contractRepository;

    public List<ProjectUpdateResponse> getAll() {
        return projectUpdateRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ProjectUpdateResponse create(ProjectUpdateRequest request) {
        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contract not found with id: " + request.getContractId()));
        return createForActiveContract(contract, request.getStage(), request.getNote());
    }

    public ProjectUpdateResponse createForActiveContract(Integer contractId, String stage, String note) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + contractId));
        return createForActiveContract(contract, stage, note);
    }

    public ProjectUpdateResponse update(Integer id, ProjectUpdateRequest request) {
        ProjectUpdate entity = projectUpdateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project update not found with id: " + id));
        entity.setContract(contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contract not found with id: " + request.getContractId())));
        entity.setStage(request.getStage());
        entity.setNote(request.getNote());
        return toResponse(projectUpdateRepository.save(entity));
    }

    public void delete(Integer id) {
        projectUpdateRepository.delete(projectUpdateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project update not found with id: " + id)));
    }

    public ProjectUpdateResponse getById(Integer id) {
        return toResponse(projectUpdateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project update not found with id: " + id)));
    }

    private ProjectUpdateResponse createForActiveContract(Contract contract, String stage, String note) {
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BadRequestException("Project updates are allowed only for ACTIVE contracts");
        }
        ProjectUpdate entity = contract.addUpdate(stage, note);
        return toResponse(projectUpdateRepository.save(entity));
    }

    private ProjectUpdateResponse toResponse(ProjectUpdate entity) {
        return ProjectUpdateResponse.builder()
                .id(entity.getId())
                .contractId(entity.getContract().getId())
                .stage(entity.getStage())
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
