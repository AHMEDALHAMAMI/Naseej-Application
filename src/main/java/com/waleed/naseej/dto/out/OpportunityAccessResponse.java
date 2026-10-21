package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.AccessType;
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
public class OpportunityAccessResponse {
    private Integer id;
    private Integer investorId;
    private Integer ideaId;
    private Integer paymentId;
    private AccessType accessType;
    private LocalDateTime priorityUntil;
}
