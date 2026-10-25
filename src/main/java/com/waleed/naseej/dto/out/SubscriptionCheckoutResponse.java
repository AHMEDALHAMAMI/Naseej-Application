package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The new subscription (PENDING until paid) and the Moyasar checkout to complete. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionCheckoutResponse {

    private SubscriptionResponse subscription;
    private InitiateMoyasarPaymentResponse checkout;
}
