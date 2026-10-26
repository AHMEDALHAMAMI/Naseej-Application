package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** What saving an address change would do: whether it is allowed, whether it uses the monthly change, and which votes it withdraws. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressChangePreviewResponse {
    private Boolean allowed;
    private Boolean countsAsChange;
    private LocalDateTime nextChangeAt;
    // Why it isn't allowed (null when allowed)
    private String reason;
    private List<WithdrawnVote> withdrawnVotes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WithdrawnVote {
        private Integer ideaId;
        private String title;
        private String neighborhoodName;
    }
}
