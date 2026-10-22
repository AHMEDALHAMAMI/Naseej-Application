package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitiateMoyasarPaymentRequest {

    @NotNull
    private Integer userId;

    @NotBlank
    private String purpose;

    /** Optional; defaults to moyasar.callback.url when omitted. */
    private String callbackUrl;

    /**
     * When true (default) and keys are test keys, Moyasar is called with the official sandbox test card.
     * Set false to receive a hosted checkout URL (3DS) and complete payment in the browser.
     */
    private Boolean useSandboxTestCard;
}
