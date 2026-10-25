package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A neighborhood bubble after zooming into a city. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NeighborhoodStatResponse {

    private Integer neighborhoodId;
    private String name;
    private String city;
    private Double latitude;
    private Double longitude;
    private Long opportunities;
}
