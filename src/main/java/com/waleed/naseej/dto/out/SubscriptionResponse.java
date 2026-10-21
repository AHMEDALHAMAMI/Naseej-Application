package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.SubscriptionPlan;
import java.time.LocalDate;
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
public class SubscriptionResponse {
    private Integer id;
    private Integer userId;
    private Integer paymentId;
    private SubscriptionPlan plan;
    private String status;
    private LocalDate startsAt;
    private LocalDate endsAt;
}
