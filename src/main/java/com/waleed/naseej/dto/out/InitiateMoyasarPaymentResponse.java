package com.waleed.naseej.dto.out;

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
public class InitiateMoyasarPaymentResponse {

    private PaymentResponse payment;
    private String moyasarStatus;
    private String transactionUrl;
    private String message;
}
