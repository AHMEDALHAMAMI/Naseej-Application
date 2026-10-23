package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.PaymentStatus;
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
public class MoyasarWebhookResponse {

    private Integer paymentId;
    private PaymentStatus paymentStatus;
    private Integer contractId;
    private ContractStatus contractStatus;
    private String message;
}
