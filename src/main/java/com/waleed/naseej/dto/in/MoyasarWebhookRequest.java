package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotBlank;
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
public class MoyasarWebhookRequest {

    /** Moyasar payment id (matches {@code Payment.moyasarPaymentId}). */
    @NotBlank
    private String id;

    /** Moyasar status, e.g. {@code paid}, {@code failed}. */
    @NotBlank
    private String status;
}
