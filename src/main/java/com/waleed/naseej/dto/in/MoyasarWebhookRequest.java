package com.waleed.naseej.dto.in;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Moyasar's webhook event: {"type": "payment_paid", "secret_token": "...", "data": { the payment }}.
 * The older flat shape {"id", "status"} is still accepted. Nothing in it is trusted on its own: the server asks
 * Moyasar for the payment before marking anything paid.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MoyasarWebhookRequest {

    // Event id (new shape) or payment id (old shape)
    private String id;
    private String status;
    private String type;

    @JsonProperty("secret_token")
    private String secretToken;

    private Map<String, Object> data;

    /** The Moyasar payment id this event is about. */
    public String paymentId() {
        if (data != null && data.get("id") instanceof String paymentId && !paymentId.isBlank()) {
            return paymentId;
        }
        return id;
    }
}
