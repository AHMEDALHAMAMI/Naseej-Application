package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An investor starts a monthly subscription; the first month is paid through Moyasar. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscribeRequest {

    // BASIC (299 SAR) or PRO (999 SAR) per month, before VAT
    @NotNull(message = "Plan is required (BASIC or PRO)")
    private SubscriptionPlan plan;

    // Where Moyasar sends the browser back after 3DS; defaults to MOYASAR_CALLBACK_URL
    private String callbackUrl;

    // Sandbox only: pay with Moyasar's test card (default true in test mode)
    private Boolean useSandboxTestCard;
}
