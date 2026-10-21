package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.AccessType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpportunityAccessRequest {

    @NotNull(message = "Investor is required")
    private Integer investorId;

    @NotNull(message = "Idea is required")
    private Integer ideaId;

    private Integer paymentId;

    @NotNull(message = "Access type is required")
    private AccessType accessType;

    private LocalDateTime priorityUntil;

    @AssertTrue(message = "PAID access needs a payment, PARTNERSHIP access must not have one")
    private boolean isPaymentConsistent() {
        if (accessType == null) {
            return true;
        }
        return accessType == AccessType.PAID ? paymentId != null : paymentId == null;
    }
}
