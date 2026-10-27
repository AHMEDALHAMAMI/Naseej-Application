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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * "My projects": adopted ideas with their execution progress, for the investor and for the founder.
 * The investor moves the project forward one stage at a time, or posts progress on the current stage (with a note),
 * so a long stage can still be updated regularly. Reaching "Opened" marks the idea as OPENED.
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
    private final NotificationService notificationService;

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
        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        if (next < current) {
            throw new BadRequestException("The project is already at \"" + STAGES.get(current) + "\". Choose a later stage");
        }
        // Progress on the current stage needs a note saying what changed
        boolean sameStage = next == current;
        if (sameStage && note == null) {
            throw new BadRequestException("Write what changed to post progress on the current stage");
        }

        projectUpdateService.createForActiveContract(contract.getId(), stage, note);
        // The founder follows the opening when it reaches a new stage (not when the founder is the investor)
        if (!sameStage && !contract.getIdeaOwner().getId().equals(contract.getInvestor().getId())) {
            notificationService.notify(contract.getIdeaOwner(), NotificationService.PROJECT_STAGE, contract.getIdea(),
                    stage, "projects.html");
        }

        if ("Opened".equals(stage)) {
            contract.setOpenedAt(LocalDateTime.now());
            contractRepository.save(contract);
            Idea idea = contract.getIdea();
            idea.setStatus(IdeaStatus.OPENED);
            ideaRepository.save(idea);
        }
        return toResponse(contractRepository.findById(contractId).orElseThrow(), "INVESTOR");
    }

    /** The project as the given party sees it (used after signing or extending). */
    public ProjectResponse toResponseFor(User user, Contract contract) {
        boolean investor = contract.getInvestor().getId().equals(user.getId());
        return toResponse(contract, investor ? "INVESTOR" : "FOUNDER");
    }

    private ProjectResponse toResponse(Contract contract, String myRole) {
        List<ProjectUpdate> updates = updatesOf(contract.getId());
        int index = currentIndex(updates);
        boolean active = contract.getStatus() == ContractStatus.ACTIVE;
        Idea idea = contract.getIdea();

        // Only the investor signs; the contract is active right after (the idea owner doesn't sign)
        boolean investorSigned = contractSignatureRepository.existsByContractIdAndSignerRole(
                contract.getId(), ContractFlowService.ROLE_INVESTOR);
        String nextStep = nextStep(contract, investorSigned);
        boolean investorView = "INVESTOR".equals(myRole);
        // The extension: only the investor sees whether they can ask for it
        String extensionBlock = investorView ? extensionBlock(contract, updates) : null;
        LocalDate extensionOpensOn = contract.getOpeningDeadline() != null && contract.getExtendedAt() == null
                ? contract.getOpeningDeadline().minusDays(ContractFlowService.EXTENSION_WINDOW_DAYS) : null;
        boolean running = active && contract.getOpenedAt() == null;
        boolean windowOpen = running && extensionOpensOn != null && !LocalDate.now().isBefore(extensionOpensOn);

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
                .investorSigned(investorSigned)
                .openingDeadline(contract.getOpeningDeadline())
                .signingDeadline(active || contract.getOpenedAt() != null ? null : contract.getSigningDeadline())
                .extended(contract.getExtendedAt() != null)
                .canExtend(investorView && extensionBlock == null)
                .extensionOpensOn(investorView && running ? extensionOpensOn : null)
                .extensionBlockedReason(investorView && windowOpen ? extensionBlock : null)
                .nextStep(nextStep)
                .canSign(investorView && "INVESTOR_SIGN".equals(nextStep))
                .build();
    }

    /**
     * Why the investor can't extend the opening deadline now (null = they can): one extension, for an active project
     * that hasn't opened, in the last EXTENSION_WINDOW_DAYS before the deadline, with a progress update in the last
     * RECENT_UPDATE_DAYS (posting regularly, as the contract asks).
     */
    public String extensionBlock(Contract contract) {
        return extensionBlock(contract, updatesOf(contract.getId()));
    }

    static String extensionBlock(Contract contract, List<ProjectUpdate> updates) {
        LocalDate deadline = contract.getOpeningDeadline();
        if (contract.getStatus() != ContractStatus.ACTIVE || contract.getOpenedAt() != null || deadline == null) {
            return "Only an active project that hasn't opened yet can extend its opening deadline";
        }
        if (contract.getExtendedAt() != null) {
            return "The opening deadline was already extended once";
        }
        LocalDate today = LocalDate.now();
        if (today.isAfter(deadline)) {
            return "The opening deadline has passed";
        }
        if (today.isBefore(deadline.minusDays(ContractFlowService.EXTENSION_WINDOW_DAYS))) {
            return "The extension opens in the last " + ContractFlowService.EXTENSION_WINDOW_DAYS
                    + " days before the opening deadline";
        }
        LocalDateTime since = LocalDateTime.now().minusDays(ContractFlowService.RECENT_UPDATE_DAYS);
        boolean recent = updates.stream().anyMatch(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(since));
        if (!recent) {
            return "Post a progress update first: the extension needs one in the last "
                    + ContractFlowService.RECENT_UPDATE_DAYS + " days";
        }
        return null;
    }

    // What has to happen next for this contract, so the page knows which button to show
    private static String nextStep(Contract contract, boolean investorSigned) {
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            return "ENDED";
        }
        if (contract.getOpenedAt() != null) {
            return "OPENED";
        }
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            return "IN_PROGRESS";
        }
        // Signed but not active yet: the purchase isn't settled
        return investorSigned ? "WAITING_PAYMENT" : "INVESTOR_SIGN";
    }

    /** Where an adopted idea's project is, e.g. "Fit-out" (index 3 of 7). Empty for ideas nobody adopted. */
    public Optional<StageInfo> stageOfIdea(Idea idea) {
        if (idea.getStatus() == IdeaStatus.OPENED) {
            return Optional.of(new StageInfo(STAGES.get(STAGES.size() - 1), STAGES.size() - 1, STAGES.size()));
        }
        if (idea.getStatus() != IdeaStatus.ADOPTED) {
            return Optional.empty();
        }
        return contractRepository.findByIdeaId(idea.getId()).stream()
                .filter(contract -> contract.getStatus() != ContractStatus.TERMINATED)
                .findFirst()
                .map(contract -> {
                    int index = currentIndex(updatesOf(contract.getId()));
                    return new StageInfo(STAGES.get(index), index, STAGES.size());
                });
    }

    public record StageInfo(String stage, int index, int count) {
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
