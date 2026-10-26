package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One pin on the resident map. Vote counts are never included here. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapIdeaResponse {

    private Integer id;
    private String title;
    private String category;
    private String status;
    private String neighborhoodName;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    // Inside the viewer's voting radius
    private Boolean inRange;
    // In range, still open, not the viewer's own idea and not voted yet
    private Boolean canVote;
    private Boolean voted;
    private Boolean mine;

    // Short brief for the map popup
    private String summary;
    // Adopted ideas: the project stage, e.g. "Licensing" (index 1 of 7)
    private String stage;
    private Integer stageIndex;
    private Integer stageCount;
}
