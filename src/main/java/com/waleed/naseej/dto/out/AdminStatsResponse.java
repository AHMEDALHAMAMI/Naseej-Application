package com.waleed.naseej.dto.out;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Numbers for the admin dashboard charts, all counted from the database. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStatsResponse {

    private List<LabelCountResponse> usersByRole;
    private List<LabelCountResponse> investorsByStatus;
    private List<LabelCountResponse> ideasByStatus;
    private List<LabelCountResponse> votesPerDay;
    private List<LabelCountResponse> accessByType;
    private List<LabelCountResponse> topNeighborhoods;
    private Long totalVotes;
    private Long pendingInvestors;
    private BigDecimal paidRevenue;
}
