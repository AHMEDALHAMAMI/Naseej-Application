package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What the browser needs to open Moyasar's payment form. The card details go from the form straight to Moyasar;
 * Naseej only gets the Moyasar payment id back and checks it with Moyasar before unlocking anything.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {
    private Integer paymentId;
    private Integer ideaId;
    private BigDecimal amount;
    private BigDecimal vat;
    private BigDecimal total;
    // total in halalas, as Moyasar expects
    private Integer amountHalalas;
    private String currency;
    private String description;
    private String publishableKey;
    private Boolean testMode;
    private Map<String, Object> metadata;
}
