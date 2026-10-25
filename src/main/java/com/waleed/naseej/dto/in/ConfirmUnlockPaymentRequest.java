package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.AssertTrue;

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
public class ConfirmUnlockPaymentRequest {

    // Our payment id, or the Moyasar id from the callback URL (?id=...)
    private Integer paymentId;

    private String moyasarPaymentId;

    @AssertTrue(message = "paymentId or moyasarPaymentId is required")
    private boolean isIdentified() {
        return paymentId != null || (moyasarPaymentId != null && !moyasarPaymentId.isBlank());
    }
}
