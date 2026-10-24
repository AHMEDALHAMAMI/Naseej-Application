package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Landing page numbers, open to everyone. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicStatsResponse {

    private Long openIdeas;
    private Long completedIdeas;
    private Long adoptedIdeas;
    private Long totalVotes;
    private Long neighborhoods;
    private Long cities;
}
