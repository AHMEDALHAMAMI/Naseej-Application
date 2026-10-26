package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.AccessType;
import com.waleed.naseej.enums.ContractStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A readable copy of the agreement behind a project, for the people in it:
 * - OWNER_SERVICE: the platform and the idea's owner (the owner launches their own idea and pays a service fee)
 * - INVESTOR_PARTNERSHIP: the investor and the founding member (a share of the net profit, no upfront amount)
 * - INVESTOR_PAID: the investor paid the platform to unlock the idea
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementResponse {

    private Integer contractId;
    private String contractNumber;
    private String agreementType;
    private AccessType accessType;
    private ContractStatus status;
    private String termsVersion;

    private Integer ideaId;
    private String ideaTitle;
    private String neighborhoodName;
    private String city;

    private String investorName;
    private String ownerName;
    // INVESTOR or FOUNDER (the investor and the founder are the same person for OWNER_SERVICE)
    private String myRole;

    // OWNER_SERVICE: the platform's share; INVESTOR_PARTNERSHIP: the founding member's share
    private BigDecimal sharePercent;
    private Integer shareYears;
    private LocalDate shareEndsAt;
    // INVESTOR_PAID
    private BigDecimal paidAmount;
    // With VAT (owner launch fee)
    private BigDecimal paidTotal;

    private LocalDate openingDeadline;
    private LocalDateTime activatedAt;
    private List<Signature> signatures;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Signature {
        private String role;
        private String typedName;
        private LocalDateTime signedAt;
    }
}
