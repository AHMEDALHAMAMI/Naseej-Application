package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.IdeaStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An admin refund and what it undid: the investor's contract (idea back to investors) or the subscription. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundResultResponse {

    private PaymentResponse payment;
    private Integer contractId;
    private ContractStatus contractStatus;
    private Integer ideaId;
    private IdeaStatus ideaStatus;
    private Integer subscriptionId;
    private String subscriptionStatus;
    private String message;
}
