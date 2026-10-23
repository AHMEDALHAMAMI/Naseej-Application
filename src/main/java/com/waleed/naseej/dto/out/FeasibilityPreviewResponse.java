package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What any investor sees before unlocking an opportunity: only the gap score. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeasibilityPreviewResponse {
    private Integer ideaId;
    private Integer gapScore;
}
