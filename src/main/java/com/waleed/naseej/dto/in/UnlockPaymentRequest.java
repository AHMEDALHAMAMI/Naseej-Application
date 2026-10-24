package com.waleed.naseej.dto.in;

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
public class UnlockPaymentRequest {

    // Where Moyasar sends the browser back after 3DS; defaults to MOYASAR_CALLBACK_URL
    private String callbackUrl;

    // Sandbox only: pay with Moyasar's test card (default true in test mode)
    private Boolean useSandboxTestCard;
}
