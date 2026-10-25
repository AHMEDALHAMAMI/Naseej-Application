package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.ProjectStageRequest;
import com.waleed.naseej.dto.out.ProjectResponse;
import com.waleed.naseej.dto.out.ProjectUpdateResponse;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.ProjectUpdate;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.ContractSignatureRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.ProjectUpdateRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * "My projects": adopted ideas with their execution progress, for the investor and for the founder.
 * The investor moves the project forward one stage at a time (at most once every 14 days, the team's rule
 * in ProjectUpdateService). Reaching "Opened" marks the idea as OPENED.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    public static final List<String> STAGES = List.of(
            "Unlocked", "Licensing", "Location secured", "Fit-out", "Hiring", "Soft opening", "Opened");

    private final ContractRepository contractRepository;
    private final ProjectUpdateRepository projectUpdateRepository;
    private final IdeaRepository ideaRepository;
    private final ProjectUpdateService projectUpdateService;
    private final ContractSignatureRepository contractSignatureRepository;

    public List<ProjectResponse> mine(User currentUser) {
        List<ProjectResponse> projects = new ArrayList<>();
        contractRepository.findByInvestorId(currentUser.getId())
                .forEach(contract -> projects.add(toResponse(contract, "INVESTOR")));
        contractRepository.findByIdeaOwnerId(currentUser.getId()).stream()
                .filter(contract -> !contract.getInvestor().getId().equals(currentUser.getId()))
                .forEach(contract -> projects.add(toResponse(contract, "FOUNDER")));
        projects.sort(Comparator.comparing(ProjectResponse::getContractId).reversed());
        return projects;
    }

    public ProjectResponse addStage(User currentUser, Integer contractId, ProjectStageRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + contractId));
        if (!contract.getInvestor().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Only the project's investor can post progress");
        }
        String stage = STAGES.stream()
                .filter(s -> s.equalsIgnoreCase(request.getStage().trim()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Stage must be one of " + STAGES));
        int current = currentIndex(updatesOf(contract.getId()));
        int next = STAGES.indexOf(stage);
        if (next <= current) {
            throw new BadRequestException("The project is already at \"" + STAGES.get(current) + "\". Choose a later stage");
        }

        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        projectUpdateService.createForActiveContract(contract.getId(), stage, note);

        if ("Opened".equals(stage)) {
            contract.setOpenedAt(LocalDateTime.now());
            contractRepository.save(contract);
            Idea idea = contract.getIdea();
            idea.setStatus(IdeaStatus.OPENED);
            ideaRepository.save(idea);
        }
        return toResponse(contractRepository.findById(contractId).orElseThrow(), "INVESTOR");
    }

    /** The project as the given party sees it (used after setting benefits or signing). */
    public ProjectResponse toResponseFor(User user, Contract contract) {
        boolean investor = contract.getInvestor().getId().equals(user.getId());
        return toResponse(contract, investor ? "INVESTOR" : "FOUNDER");
    }

    private ProjectResponse toResponse(Contract contract, String myRole) {
        List<ProjectUpdate> updates = updatesOf(contract.getId());
        int index = currentIndex(updates);
        boolean active = contract.getStatus() == ContractStatus.ACTIVE;
        Idea idea = contract.getIdea();

        // Signing progress: an investor who adopted their own idea signs once, for both sides
        boolean selfOwned = Objects.equals(contract.getInvestor().getId(), contract.getIdeaOwner().getId());
        boolean investorSigned = contractSignatureRepository.existsByContractIdAndSignerRole(
                contract.getId(), ContractFlowService.ROLE_INVESTOR);
        boolean founderSigned = selfOwned ? investorSigned : contractSignatureRepository.existsByContractIdAndSignerRole(
                contract.getId(), ContractFlowService.ROLE_IDEA_OWNER);
        boolean benefitsSet = contract.getBenefitsSetAt() != null;
        String nextStep = nextStep(contract, benefitsSet, investorSigned, founderSigned);
        boolean investorView = "INVESTOR".equals(myRole);

        return ProjectResponse.builder()
                .contractId(contract.getId())
                .contractNumber(contract.getContractNumber())
                .ideaId(idea.getId())
                .ideaTitle(idea.getTitle())
                .category(idea.getCategory())
                .neighborhoodName(idea.getNeighborhood().getName())
                .city(idea.getNeighborhood().getCity())
                .myRole(myRole)
                .accessType(contract.getAccess().getAccessType())
                .investorName(contract.getInvestor().getFullName())
                .founderName(contract.getIdeaOwner().getFullName())
                .contractStatus(contract.getStatus())
                .activatedAt(contract.getActivatedAt())
                .stages(STAGES)
                .currentStage(STAGES.get(index))
                .stageIndex(index)
                .progressPercent(Math.round(index * 100f / (STAGES.size() - 1)))
                .updates(updates.stream().map(this::toUpdateResponse).toList())
                // The investor can post the next stage any time; there is no waiting period
                .canPostUpdate("INVESTOR".equals(myRole) && active && index < STAGES.size() - 1)
                .nextUpdateAllowedAt(null)
                .foundingMemberDiscount(contract.getFoundingMemberDiscount())
                .ideaOwnerDiscount(contract.getIdeaOwnerDiscount())
                .benefitsNote(contract.getBenefitsNote())
                .benefitsSet(benefitsSet)
                .investorSigned(investorSigned)
                .founderSigned(founderSigned)
                .openingDeadline(contract.getOpeningDeadline())
                .nextStep(nextStep)
                .canSetBenefits(investorView && contract.getStatus() == ContractStatus.DRAFT)
                .canSign((investorView && "INVESTOR_SIGN".equals(nextStep))
                        || (!investorView && "FOUNDER_SIGN".equals(nextStep)))
                .build();
    }

    // What has to happen next for this contract, so the page knows which button to show
    private static String nextStep(Contract contract, boolean benefitsSet, boolean investorSigned, boolean founderSigned) {
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            return "ENDED";
        }
        if (contract.getOpenedAt() != null) {
            return "OPENED";
        }
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            return "IN_PROGRESS";
        }
        if (!benefitsSet) {
            return "SET_BENEFITS";
        }
        if (!investorSigned) {
            return "INVESTOR_SIGN";
        }
        return founderSigned ? "WAITING_PAYMENT" : "FOUNDER_SIGN";
    }

    private List<ProjectUpdate> updatesOf(Integer contractId) {
        return projectUpdateRepository.findByContractIdOrderByCreatedAtAsc(contractId);
    }

    // "Unlocked" (index 0) until the first update; afterwards the furthest known stage
    private static int currentIndex(List<ProjectUpdate> updates) {
        int furthest = updates.stream().mapToInt(update -> STAGES.indexOf(update.getStage())).max().orElse(0);
        return Math.max(0, furthest);
    }

    private ProjectUpdateResponse toUpdateResponse(ProjectUpdate update) {
        return ProjectUpdateResponse.builder()
                .id(update.getId())
                .contractId(update.getContract().getId())
                .stage(update.getStage())
                .note(update.getNote())
                .createdAt(update.getCreatedAt())
                .build();
    }
}
