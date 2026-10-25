package com.waleed.naseej.dto.out;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Result of the 90-day check: contracts terminated because the project didn't open in time. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpiredContractsResponse {

    private Integer expiredCount;
    private List<String> contractNumbers;
    // Ideas that went back to investors (COMPLETED) or back to voting (PUBLISHED)
    private List<Integer> reopenedIdeaIds;
    private LocalDateTime checkedAt;
}
