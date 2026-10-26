package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.AccessType;
import com.waleed.naseej.enums.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An adopted idea as seen on "My projects" by its investor or its founder. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {

    private Integer contractId;
    private String contractNumber;
    private Integer ideaId;
    private String ideaTitle;
    private String category;
    private String neighborhoodName;
    private String city;
    // INVESTOR or FOUNDER
    private String myRole;
    private AccessType accessType;
    private String investorName;
    private String founderName;
    private ContractStatus contractStatus;
    private LocalDateTime activatedAt;
    private List<String> stages;
    private String currentStage;
    private Integer stageIndex;
    private Integer progressPercent;
    private List<ProjectUpdateResponse> updates;
    private Boolean canPostUpdate;
    private LocalDateTime nextUpdateAllowedAt;

    // ---------- Contract: benefits and signatures (DRAFT → INVESTOR_SIGNED → ACTIVE) ----------
    private BigDecimal ideaOwnerDiscount;
    private String benefitsNote;
    private Boolean benefitsSet;
    private Boolean investorSigned;
    private Boolean founderSigned;
    private LocalDate openingDeadline;
    // Until both parties sign: the last day to sign before the idea goes back to other investors
    private LocalDate signingDeadline;
    // The one extension of the opening deadline: used already, can be asked for now, or when it opens
    private Boolean extended;
    private Boolean canExtend;
    private LocalDate extensionOpensOn;
    // Why the investor can't extend yet although the window is open (e.g. no recent progress update)
    private String extensionBlockedReason;
    // SET_BENEFITS, INVESTOR_SIGN, FOUNDER_SIGN, WAITING_PAYMENT, IN_PROGRESS, OPENED or ENDED
    private String nextStep;
    private Boolean canSetBenefits;
    private Boolean canSign;
}
