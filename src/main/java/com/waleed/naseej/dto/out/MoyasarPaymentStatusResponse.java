package com.waleed.naseej.dto.out;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/** A payment as Moyasar reports it (GET /v1/payments/{id}). Amounts are in halalas. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MoyasarPaymentStatusResponse {
    private String id;
    private String status;
    private Integer amount;
    private String currency;
    // What Naseej attached when the payment form was opened (naseej_payment_id, idea_id)
    private Map<String, Object> metadata;
    private Source source;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        private String type;
        private String company;
        // Why a card payment failed, e.g. "Insufficient funds"
        private String message;
    }
}
