package com.waleed.naseej.dto.out;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The investor's search: the AI summary (null when the keyword search was used) and the matching opportunities. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSearchResponse {

    private String query;
    private String summary;
    private Boolean aiUsed;
    private List<OpportunityResponse> results;
}
