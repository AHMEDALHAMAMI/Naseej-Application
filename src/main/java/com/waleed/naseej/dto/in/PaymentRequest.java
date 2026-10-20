package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.PaymentMethod;
import com.waleed.naseej.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class PaymentRequest {

    private Integer userId;
    private String purpose;
    private BigDecimal amount;
    private BigDecimal vat;
    private BigDecimal totalAmount;
    private String currency;
    private PaymentMethod method;
    private String moyasarPaymentId;
    private PaymentStatus status;
    private String invoiceUrl;
    private LocalDateTime paidAt;

}
